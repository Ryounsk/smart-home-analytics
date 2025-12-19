package com.smarthome.online.config;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 读取HDFS日志，发送到Kafka（统一字段名，匹配Flink作业解析逻辑）
 */
public class HdfsToKafkaConfig {
    // 项目文档指定的Kafka主题（替换原api_log_topic）
    private static String KAFKA_TOPIC = "test_api_log";
    private static String KAFKA_BOOTSTRAP_SERVERS = "hadoop102:9092,hadoop103:9092,hadoop104:9092";
    private static final String HDFS_FILE_PATH = "hdfs://hadoop102:8020/flink/api_log.txt";
    private static final String HADOOP_USER = "atguigu";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int BATCH_SIZE = 200;

    public static void main(String[] args) {
        // 解析启动参数（支持自定义主题和Kafka地址）
        if (args.length >= 1) KAFKA_TOPIC = args[0];
        if (args.length >= 2) KAFKA_BOOTSTRAP_SERVERS = args[1];

        sendHdfsLogToKafka(HDFS_FILE_PATH, KAFKA_TOPIC, KAFKA_BOOTSTRAP_SERVERS);
    }

    /**
     * 核心方法：HDFS日志发送到Kafka
     */
    public static void sendHdfsLogToKafka(String hdfsFilePath, String kafkaTopic, String kafkaBootstrapServers) {
        KafkaProducer<String, String> producer = new KafkaProducer<>(initKafkaConfig(kafkaBootstrapServers));
        Configuration hadoopConf = new Configuration();
        hadoopConf.set("HADOOP_USER_NAME", HADOOP_USER);

        try (FileSystem fs = FileSystem.get(new Path(hdfsFilePath).toUri(), hadoopConf);
             FSDataInputStream inputStream = fs.open(new Path(hdfsFilePath));
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            int successCount = 0;
            final int[] failCount = {0};
            reader.readLine(); // 跳表头

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    failCount[0]++;
                    continue;
                }

                // 解析日志为JSON（统一字段名：logId、apiName等，匹配Flink解析）
                String json = parseLogToJson(line);
                if (json == null) {
                    failCount[0]++;
                    continue;
                }

                // 发送到Kafka（用logId作为Key）
                String logId = extractLogId(line);
                producer.send(new ProducerRecord<>(kafkaTopic, logId, json), (metadata, exception) -> {
                    if (exception != null) {
                        logger.error("Kafka发送失败：logId={}", logId, exception);
                        failCount[0]++;
                    }
                });

                // 批量刷新
                if (++successCount % BATCH_SIZE == 0) {
                    producer.flush();
                    logger.info("已发送 {} 条，失败 {} 条", successCount, failCount[0]);
                }
            }

            // 发送完成统计
            producer.flush();
            int total = successCount + failCount[0];
            logger.info("=== 发送完成 ===");
            logger.info("总条数：{}，成功：{}，失败：{}，成功率：{:.2f}%",
                    total, successCount, failCount[0], total == 0 ? 0 : (double) successCount / total * 100);

        } catch (Exception e) {
            logger.error("HDFS读取或Kafka发送异常", e);
        } finally {
            producer.close();
        }
    }

    /**
     * 初始化Kafka配置
     */
    private static Properties initKafkaConfig(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        return props;
    }

    /**
     * 解析日志为JSON（统一字段名：logId、apiName、apiResponseTime、apiCallStatus、apiCallDate）
     */
    /**
     * 解析日志为JSON（适配制表符分隔、无键名前缀的格式）
     */
    private static String parseLogToJson(String line) {
        try {
            Map<String, Object> logMap = new HashMap<>();
            // 1. 按制表符分割字段（原逻辑是按逗号分割，需修改）
            String[] fields = line.split("\t");
            // 检查字段数量是否符合预期（至少5个字段）
            if (fields.length < 5) {
                logger.warn("字段数量不足（跳过）：{}", line);
                return null;
            }

            // 2. 按固定顺序提取字段（去除首尾引号）
            String logId = fields[0].replace("\"", "").trim();       // 第1个字段：log_id
            String apiName = fields[1].replace("\"", "").trim();     // 第2个字段：api_name
            String responseTime = fields[2].replace("\"", "").trim();// 第3个字段：api_response_time
            String callStatus = fields[3].replace("\"", "").trim();  // 第4个字段：api_call_status
            String callDate = fields[4].replace("\"", "").trim();    // 第5个字段：api_call_date

            // 3. 填充核心字段（匹配Flink解析的驼峰字段）
            logMap.put("logId", logId);
            logMap.put("apiName", apiName);
            logMap.put("apiResponseTime", responseTime);
            logMap.put("apiCallStatus", callStatus);
            logMap.put("apiCallDate", callDate);

            // 4. 空值校验（确保核心字段非空）
            if (logId.isEmpty() || apiName.isEmpty()) {
                logger.warn("关键字段为空（跳过）：logId={}, apiName={}, 行数据：{}", logId, apiName, line);
                return null;
            }

            return objectMapper.writeValueAsString(logMap);
        } catch (JsonProcessingException e) {
            logger.error("JSON解析失败（跳过）：{}", line, e);
            return null;
        }
    }

    /**
     * 提取logId作为Kafka Key
     */
    /**
     * 提取logId作为Kafka Key（适配新格式）
     */
    private static String extractLogId(String line) {
        try {
            String[] fields = line.split("\t");
            if (fields.length >= 1) {
                String logId = fields[0].replace("\"", "").trim();
                return logId.isEmpty() ? "unknown_" + System.currentTimeMillis() : logId;
            }
            // 字段不足时返回默认值
            return "unknown_" + System.currentTimeMillis();
        } catch (Exception e) {
            return "unknown_" + System.currentTimeMillis();
        }
    }

    // 新增日志打印（避免依赖外部日志框架）
    private static final Logger logger = LoggerFactory.getLogger(HdfsToKafkaConfig.class);
}