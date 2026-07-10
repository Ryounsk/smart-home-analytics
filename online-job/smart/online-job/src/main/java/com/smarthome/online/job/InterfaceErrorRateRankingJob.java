package com.smarthome.online.job;

import com.smarthome.online.config.CommonConfig;
import com.smarthome.online.config.HdfsToKafkaConfig;
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
import java.util.*;

/**
 * 接口1：实时计算接口错误率排行榜（方案一：ZSet直接存JSON字符串作为member，score为错误率）
 * 已修改：滑动窗口改滚动窗口 + 方案一Redis写入逻辑
 */
public class InterfaceErrorRateRankingJob {
    private static final Logger logger = LoggerFactory.getLogger(InterfaceErrorRateRankingJob.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(CommonConfig.TIME_FORMAT_PATTERN);

    public static void main(String[] args) throws Exception {
        // 第一步：执行HDFS→Kafka数据同步（独立执行，避免重复发送）
        if (args.length > 0 && "syncData".equals(args[0])) {
            logger.info("开始将HDFS日志同步到Kafka主题：{}", CommonConfig.KAFKA_TOPIC);
            String hdfsPath = "hdfs://hadoop102:8020/flink/api_log.txt";
            HdfsToKafkaConfig.sendHdfsLogToKafka(hdfsPath, CommonConfig.KAFKA_TOPIC, CommonConfig.KAFKA_BOOTSTRAP_SERVERS);
            logger.info("HDFS→Kafka数据同步完成");
            return;
        }

        // 第二步：Flink实时计算逻辑
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.enableCheckpointing(60000); // 1分钟Checkpoint
        env.setMaxParallelism(128);

        // Kafka消费者配置
        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", CommonConfig.KAFKA_BOOTSTRAP_SERVERS);
        kafkaProps.setProperty("group.id", "flink-error-rate-group"); // 独立消费者组
        kafkaProps.setProperty("auto.offset.reset", "earliest");
        kafkaProps.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        kafkaProps.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        kafkaProps.setProperty("fetch.max.wait.ms", "500");
        kafkaProps.setProperty("max.poll.records", "100");

        // 读取Kafka数据
        DataStream<String> kafkaSource = env.addSource(
                new FlinkKafkaConsumer<>(CommonConfig.KAFKA_TOPIC, new SimpleStringSchema(), kafkaProps)
        ).name("Kafka-Error-Rate-Source");

        // 解析JSON为实体类（增加空指针防护）
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
                    String callDateStr = getSafeValue(dataMap, "apiCallDate", LocalDateTime.now().format(TIME_FORMATTER));
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

        // 滚动窗口计算错误率（消除重叠统计）
        DataStream<ErrorRateResult> errorRateStream = validLogStream
                .keyBy(log -> generateInterfaceId(log.getApiName()))
                .window(TumblingProcessingTimeWindows.of(Time.milliseconds(CommonConfig.WINDOW_SIZE)))
                .aggregate(new ErrorRateAggregator())
                .name("TumblingWindow-ErrorRate-Calc");

        // 结果写入Redis（方案一：ZSet的member为JSON字符串，score为错误率）
        errorRateStream.addSink(new SinkFunction<ErrorRateResult>() {
            @Override
            public void invoke(ErrorRateResult result, Context context) throws Exception {
                if (result == null || result.getInterfaceId() == null) {
                    logger.warn("跳过空的错误率结果");
                    return;
                }

                String json = JsonUtil.toJson(result);
                double errorRate = result.getErrorRate();

                try (Jedis jedis = RedisUtil.getJedis()) {
                    // 方案一核心：ZSet存（score=错误率，member=JSON字符串）
                    // 注意：如果需要去重，可先根据interfaceId删除旧的member（这里用模糊匹配，也可单独存interfaceId和JSON的映射）
                    // 可选去重逻辑：先删除该接口的旧数据（根据interfaceId模糊匹配）
                    List<String> oldMembers = jedis.zrange(CommonConfig.REDIS_ERROR_RATE_RANKING, 0, -1);
                    for (String member : oldMembers) {
                         ErrorRateResult oldResult = JsonUtil.fromJson(member, ErrorRateResult.class);
                         if (oldResult != null && oldResult.getInterfaceId().equals(result.getInterfaceId())) {
                             jedis.zrem(CommonConfig.REDIS_ERROR_RATE_RANKING, member);
                             break;
                         }
                     }

                    // 写入ZSet（自动按score排序，相同member会覆盖，不同interfaceId的JSON是不同member）
                    jedis.zadd(CommonConfig.REDIS_ERROR_RATE_RANKING, errorRate, json);
                    jedis.expire(CommonConfig.REDIS_ERROR_RATE_RANKING, 3600); // 1小时过期

                    logger.info("错误率写入Redis成功：interfaceId={}, errorRate={}", result.getInterfaceId(), errorRate);
                } catch (Exception e) {
                    logger.error("Redis写入失败！interfaceId={}", result.getInterfaceId(), e);
                    throw new RuntimeException("Redis写入失败", e);
                }
            }
        }).name("Redis-Error-Rate-Sink");

        // 提交作业
        env.execute("Interface Error Rate Ranking Job");
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
    private static String generateInterfaceId(String apiName) {
        return "interface_" + apiName;
    }

    /**
     * 错误率聚合函数
     */
    public static class ErrorRateAggregator implements AggregateFunction<
            DeviceInterfaceLog,
            Tuple3<String, Long, Long>,
            ErrorRateResult> {

        @Override
        public Tuple3<String, Long, Long> createAccumulator() {
            return Tuple3.of("", 0L, 0L);
        }

        @Override
        public Tuple3<String, Long, Long> add(DeviceInterfaceLog log, Tuple3<String, Long, Long> acc) {
            String interfaceId = generateInterfaceId(log.getApiName());
            long totalCount = acc.f1 + 1;
            long errorCount = acc.f2 + (log.isFail() ? 1 : 0);
            return Tuple3.of(interfaceId, totalCount, errorCount);
        }

        @Override
        public ErrorRateResult getResult(Tuple3<String, Long, Long> acc) {
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
            return Tuple3.of(a.f0, a.f1 + b.f1, a.f2 + b.f2);
        }
    }
}