package com.smarthome.online.job;

import com.smarthome.online.config.CommonConfig;
import com.smarthome.online.entity.CircuitBreakerInfo;
import com.smarthome.online.entity.DeviceInterfaceLog;
import com.smarthome.online.entity.ErrorRateResult;
import com.smarthome.online.util.JsonUtil;
import com.smarthome.online.util.RedisUtil;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 接口2：熔断接口列表（错误率≥5%的接口）
 * 已修改：1.滑动窗口改滚动窗口 2.Redis Hash用纯interfaceId覆盖 3.内置聚合函数避免外部依赖 4.统一异常处理
 */
public class CircuitBreakerInterfaceJob {
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerInterfaceJob.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(CommonConfig.TIME_FORMAT_PATTERN);

    public static void main(String[] args) throws Exception {
        // 初始化Flink执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.enableCheckpointing(60000); // 1分钟Checkpoint
        env.setMaxParallelism(128);

        // Kafka消费者配置
        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", CommonConfig.KAFKA_BOOTSTRAP_SERVERS);
        kafkaProps.setProperty("group.id", "flink-circuit-breaker-group"); // 独立消费者组
        kafkaProps.setProperty("auto.offset.reset", "earliest");
        kafkaProps.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        kafkaProps.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        kafkaProps.setProperty("fetch.max.wait.ms", "500");
        kafkaProps.setProperty("max.poll.records", "100");

        // 读取Kafka日志
        DataStream<String> kafkaSource = env.addSource(
                new FlinkKafkaConsumer<>(CommonConfig.KAFKA_TOPIC, new SimpleStringSchema(), kafkaProps)
        ).name("Kafka-Circuit-Breaker-Source");

        // 解析Kafka JSON为DeviceInterfaceLog（增加空指针防护）
        DataStream<DeviceInterfaceLog> logStream = kafkaSource
                .map(json -> {
                    Map<String, Object> dataMap = JsonUtil.fromJson(json, Map.class);
                    if (dataMap == null) dataMap = new HashMap<>();

                    DeviceInterfaceLog log = new DeviceInterfaceLog();
                    log.setLogId(getSafeValue(dataMap, "logId", "log_" + System.currentTimeMillis()));
                    log.setApiName(getSafeValue(dataMap, "apiName", ""));
                    log.setApiResponseTime(getSafeValue(dataMap, "apiResponseTime", "0ms"));
                    log.setApiCallStatus(getSafeValue(dataMap, "apiCallStatus", "success"));

                    // 时间解析防护
                    String callDateStr = getSafeValue(dataMap, "callDate", LocalDateTime.now().format(TIME_FORMATTER));
                    log.parseCallDate(callDateStr);

                    return log;
                })
                .name("JSON-To-DeviceInterfaceLog");

        // 数据预处理
        DataStream<DeviceInterfaceLog> validLogStream = logStream
                .filter(log -> log.getApiName() != null && !log.getApiName().isEmpty())
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<DeviceInterfaceLog>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                .withTimestampAssigner((log, ts) ->
                                        log.getApiCallDate().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                )
                )
                .name("Filter-Valid-Log");

        // 核心修改1：滑动窗口改为滚动窗口（消除重叠统计）
        DataStream<ErrorRateResult> errorRateStream = validLogStream
                .keyBy(log -> generateInterfaceId(log.getApiName()))
                .window(TumblingProcessingTimeWindows.of(Time.milliseconds(CommonConfig.WINDOW_SIZE)))
                .aggregate(new ErrorRateAggregator()) // 使用内置的聚合函数
                .name("TumblingWindow-ErrorRate-Calc");

        // 筛选熔断接口（错误率≥5%）
        DataStream<CircuitBreakerInfo> circuitBreakerStream = errorRateStream
                .filter(result -> result.getErrorRate() >= CommonConfig.BREAKER_THRESHOLD)
                .map(result -> {
                    String apiName = result.getInterfaceId().replace("interface_", "");
                    return new CircuitBreakerInfo(
                            result.getInterfaceId(),
                            apiName,
                            result.getErrorRate(),
                            result.getLastUpdateTime()
                    );
                })
                .name("Filter-Circuit-Breaker-Interface");

        // 结果写入Redis Hash（核心修改2：用纯interfaceId作为field，强制覆盖）
        circuitBreakerStream.addSink(new SinkFunction<CircuitBreakerInfo>() {
            @Override
            public void invoke(CircuitBreakerInfo breakerInfo, Context context) throws Exception {
                if (breakerInfo == null || breakerInfo.getInterfaceId() == null) {
                    logger.warn("跳过空的熔断接口数据");
                    return;
                }

                // 核心修改：去掉时间戳，只用interfaceId作为Hash的field
                String interfaceId = breakerInfo.getInterfaceId();
                String breakerJson = JsonUtil.toJson(breakerInfo);

                try (Jedis jedis = RedisUtil.getJedis()) {
                    // 同一interfaceId会直接覆盖旧值，实现按接口ID去重
                    jedis.hset(CommonConfig.REDIS_CIRCUIT_BREAKER, interfaceId, breakerJson);
                    jedis.expire(CommonConfig.REDIS_CIRCUIT_BREAKER, 3600); // 1小时过期
                    logger.info("熔断接口写入Redis成功：interfaceId={}, apiName={}, errorRate={}",
                            interfaceId, breakerInfo.getApiName(), breakerInfo.getErrorRate());
                } catch (Exception e) {
                    logger.error("熔断接口写入Redis失败：interfaceId={}", breakerInfo.getInterfaceId(), e);
                    throw new RuntimeException("Redis写入失败", e); // 让Flink感知错误，触发重试机制
                }
            }
        }).name("Redis-Circuit-Breaker-Sink");

        // 提交作业
        env.execute("Circuit Breaker Interface Job");
    }

    /**
     * 安全获取Map值，避免空指针
     */
    private static String getSafeValue(Map<String, Object> dataMap, String key, String defaultValue) {
        Object value = dataMap.get(key);
        return value == null ? defaultValue : value.toString();
    }

    /**
     * 生成统一格式接口ID（interface_xxx）
     */
    public static String generateInterfaceId(String apiName) {
        return "interface_" + apiName;
    }

    /**
     * 内置错误率聚合函数（避免依赖InterfaceErrorRateRankingJob的内部类）
     */
    public static class ErrorRateAggregator implements AggregateFunction<
            DeviceInterfaceLog,
            Tuple3<String, Long, Long>,
            ErrorRateResult> {

        @Override
        public Tuple3<String, Long, Long> createAccumulator() {
            // 初始化：interfaceId为空，总请求数为0，错误请求数为0
            return Tuple3.of("", 0L, 0L);
        }

        @Override
        public Tuple3<String, Long, Long> add(DeviceInterfaceLog log, Tuple3<String, Long, Long> acc) {
            String interfaceId = generateInterfaceId(log.getApiName());
            long totalCount = acc.f1 + 1; // 总请求数+1
            // 错误请求数：如果状态是失败则+1，否则保持不变
            long errorCount = acc.f2 + (log.isFail() ? 1 : 0);
            return Tuple3.of(interfaceId, totalCount, errorCount);
        }

        @Override
        public ErrorRateResult getResult(Tuple3<String, Long, Long> acc) {
            // 计算错误率：错误数/总数，保留4位小数
            double errorRate = acc.f1 == 0 ? 0.0 : Math.round((double) acc.f2 / acc.f1 * 10000) / 10000.0;
            return new ErrorRateResult(
                    acc.f0,
                    errorRate,
                    acc.f1,
                    acc.f2,
                    LocalDateTime.now().format(TIME_FORMATTER)
            );
        }

        @Override
        public Tuple3<String, Long, Long> merge(Tuple3<String, Long, Long> a, Tuple3<String, Long, Long> b) {
            // 合并两个累加器：总请求数和错误请求数相加
            return Tuple3.of(a.f0, a.f1 + b.f1, a.f2 + b.f2);
        }
    }
}