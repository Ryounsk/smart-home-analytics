package com.smarthome.online.job;

import com.smarthome.online.entity.DeviceInterfaceLog;
import com.smarthome.online.entity.ErrorRateResult;
import com.smarthome.online.util.DBUtil;
import com.smarthome.online.util.JsonUtil;
import com.smarthome.online.util.RedisUtil;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Flink流处理作业：负责读取MySQL api_log、计算接口错误率、写入Redis
 */
public class InterfaceErrorRateRankingJob {
    private static final Logger logger = LoggerFactory.getLogger(InterfaceErrorRateRankingJob.class);
    // 使用默认配置
    private static final long WINDOW_SIZE = 60000; // 5分钟窗口
    private static final long SLIDE_SIZE = 30000; // 1分钟滑动步长
    private static final String REDIS_RANKING_KEY = "interface_error_rate_ranking";
    private static final int INTERFACE_COUNT = 15;
    private static final double BREAKER_THRESHOLD = 0.05; // 熔断阈值5%

    // 时间格式化器
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) throws Exception {
        // 1. 初始化Flink执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1); // 并行度匹配集群TaskManager数量
        env.enableCheckpointing(60000); // 1分钟Checkpoint
        env.setMaxParallelism(128); // 避免KeyGroup异常

        // 2. 从MySQL读取api_log表数据
        DataStream<DeviceInterfaceLog> mysqlSource = env.addSource(new RichSourceFunction<DeviceInterfaceLog>() {
            private volatile boolean isRunning = true;
            private Connection conn;
            private PreparedStatement pstmt;
            private LocalDateTime lastQueryTime; // 避免重复读取
            private transient DBUtil dbUtil;

            @Override
            public void open(Configuration parameters) throws Exception {
                super.open(parameters);
                // 初始化DBUtil，仅获取一次连接
                this.dbUtil = new DBUtil();
                this.conn = dbUtil.getConnection();
                // 初始查询最近30分钟数据，避免一次性读取全表
                lastQueryTime = LocalDateTime.now().minusMinutes(30);
                // 读取api_log表的SQL
                String sql = "SELECT log_id, api_name, api_response_time, api_call_status, api_call_date " +
                        "FROM api_log " +
                        "WHERE api_call_date > ? " +
                        "ORDER BY api_call_date ASC";
                pstmt = conn.prepareStatement(sql);
                logger.info("MySQL数据源初始化成功，初始查询时间：{}", lastQueryTime);
            }

            @Override
            public void run(SourceContext<DeviceInterfaceLog> ctx) throws Exception {
                while (isRunning) {
                    pstmt.setTimestamp(1, Timestamp.valueOf(lastQueryTime));
                    ResultSet rs = pstmt.executeQuery();
                    int count = 0; // 统计本次拉取的数据量
                    while (rs.next()) {
                        DeviceInterfaceLog log = new DeviceInterfaceLog();
                        // 映射api_log表字段到实体类
                        log.setLogId(rs.getString("log_id"));
                        log.setApiName(rs.getString("api_name"));
                        log.setApiResponseTime(rs.getString("api_response_time"));
                        log.setApiCallStatus(rs.getString("api_call_status"));
                        log.setApiCallDate(rs.getTimestamp("api_call_date").toLocalDateTime());

                        // 收集数据到Flink流
                        ctx.collect(log);
                        count++;

                        // 更新上次查询时间
                        if (log.getApiCallDate().isAfter(lastQueryTime)) {
                            lastQueryTime = log.getApiCallDate();
                        }
                    }
                    rs.close();
                    logger.info("本次从MySQL拉取{}条api_log数据", count);
                    Thread.sleep(10000); // 每10秒拉取一次新数据
                }
            }

            @Override
            public void cancel() {
                isRunning = false;
                // 关闭数据库连接
                DBUtil.close(conn, pstmt, null);
                logger.info("MySQL数据源已关闭");
            }
        }).name("MySQL-API-Log-Source");

        // 3. 数据预处理：过滤非空的apiName
        DataStream<DeviceInterfaceLog> validLogStream = mysqlSource
                .filter(log -> log.getApiName() != null && !log.getApiName().isEmpty())
                .assignTimestampsAndWatermarks(
                        // 处理乱序数据，设置5秒水位线延迟
                        WatermarkStrategy.<DeviceInterfaceLog>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                .withTimestampAssigner((log, ts) -> log.getApiCallDate().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                ).name("Filter-Valid-Log");

        // 4. 滑动窗口计算错误率
        DataStream<ErrorRateResult> errorRateStream = validLogStream
                .keyBy(log -> generateInterfaceId(log.getApiName())) // 按接口ID分组
                .window(SlidingProcessingTimeWindows.of(Time.milliseconds(WINDOW_SIZE), Time.milliseconds(SLIDE_SIZE)))
                .aggregate(new ErrorRateAggregator())
                .name("SlidingWindow-ErrorRate-Calc");

        // 5. 结果写入Redis
        errorRateStream.addSink(new SinkFunction<ErrorRateResult>() {
            @Override
            public void invoke(ErrorRateResult result, Context context) throws Exception {
                // 序列化结果为JSON，写入Redis有序集合
                String json = JsonUtil.toJson(result);
                RedisUtil.zadd(REDIS_RANKING_KEY, result.getErrorRate(), json);
                logger.info("错误率结果写入Redis：interfaceId={}, errorRate={}",
                        result.getInterfaceId(), result.getErrorRate());
            }
        }).name("Redis-ErrorRate-Sink");

        // 6. 提交Flink作业到集群
        env.execute("Interface Error Rate Ranking Job（仅流处理）");
    }

    /**
     * 动态生成接口ID：apiName→interface_xxx
     */
    private static String generateInterfaceId(String apiName) {
        return "interface_" + apiName;
    }

    /**
     * 自定义聚合函数：计算每个接口的错误率
     */
    public static class ErrorRateAggregator implements AggregateFunction<
            DeviceInterfaceLog,
            Tuple3<String, Long, Long>, // 累加器：(interfaceId, 总次数, 错误次数)
            ErrorRateResult> {

        @Override
        public Tuple3<String, Long, Long> createAccumulator() {
            return Tuple3.of("", 0L, 0L);
        }

        @Override
        public Tuple3<String, Long, Long> add(DeviceInterfaceLog log, Tuple3<String, Long, Long> acc) {
            String interfaceId = generateInterfaceId(log.getApiName());
            // 累加总请求次数
            long totalCount = acc.f1 + 1;
            // 累加错误次数
            long errorCount = acc.f2 + (log.isFail() ? 1 : 0);
            return Tuple3.of(interfaceId, totalCount, errorCount);
        }

        @Override
        public ErrorRateResult getResult(Tuple3<String, Long, Long> acc) {
            // 计算错误率
            double errorRate = acc.f1 == 0 ? 0.0 : (double) acc.f2 / acc.f1;
            errorRate = Math.round(errorRate * 10000) / 10000.0;
            // 返回计算结果
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
            // 合并两个累加器
            return Tuple3.of(a.f0, a.f1 + b.f1, a.f2 + b.f2);
        }
    }
}