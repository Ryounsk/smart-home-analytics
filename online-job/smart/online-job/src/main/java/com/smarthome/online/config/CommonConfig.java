package com.smarthome.online.config;

/**
 * 在线模块公共配置常量（统一管理，避免重复定义）
 */
public class CommonConfig {
    // Kafka配置（项目文档指定主题device-interface-logs）
    public static final String KAFKA_BOOTSTRAP_SERVERS = "hadoop102:9092,hadoop103:9092,hadoop104:9092";
    public static final String KAFKA_TOPIC = "test_api_log"; // 统一主题

    // 窗口配置（按项目文档统一为5分钟窗口+1分钟滑动）
    public static final long WINDOW_SIZE = 10000; // 10秒（毫秒）
    public static final long SLIDE_SIZE = 5000;   // 5秒（毫秒）

    // 熔断阈值（项目文档指定5%）
    public static final double BREAKER_THRESHOLD = 0.05;

    // Redis Key配置（区分不同功能）
    public static final String REDIS_ERROR_RATE_RANKING = "interface_error_rate_ranking";
    public static final String REDIS_CIRCUIT_BREAKER = "interface_circuit_breaker";
    public static final String REDIS_NORMAL_INTERFACE = "interface_normal_list";

    // 时间格式化器（统一格式）
    public static final String TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String KEY_TIME_FORMAT_PATTERN = "yyyyMMddHHmmss";
}