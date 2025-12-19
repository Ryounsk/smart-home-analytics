package com.smarthome.online.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JSON工具类（优化序列化配置，解决反序列化失败问题）
 */
public class JsonUtil {
    private static final Logger logger = LoggerFactory.getLogger(JsonUtil.class);

    // 初始化ObjectMapper，添加全面的序列化配置
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 静态代码块：配置ObjectMapper
    static {
        // 1. 注册JSR310模块（支持LocalDateTime、LocalDate等JDK8日期类型）
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        // 自定义LocalDateTime的序列化/反序列化格式（和业务中使用的格式一致）
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFormatter));
        objectMapper.registerModule(javaTimeModule);

        // 2. 序列化配置：允许空对象、关闭日期转为时间戳
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS); // 允许空对象序列化
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // 日期不转为时间戳

        // 3. 反序列化配置：忽略未知字段、允许空值（关键：解决字段不匹配/构造器参数问题）
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES); // 忽略JSON中存在但实体类没有的字段
        objectMapper.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES); // 允许基本类型接收null值
        objectMapper.disable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES); // 允许构造器参数为null
    }

    // 对象转JSON字符串
    public static String toJson(Object obj) {
        try {
            if (obj == null) {
                return "{}";
            }
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            logger.error("对象转JSON失败，对象类型：{}", obj.getClass().getName(), e);
            return "{}";
        }
    }

    // JSON字符串转对象
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            if (json == null || json.isEmpty() || "{}".equals(json)) {
                logger.warn("JSON字符串为空或无效，无法转换为对象：{}", clazz.getName());
                return null;
            }
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            logger.error("JSON转对象失败，JSON：{}，目标类型：{}", json, clazz.getName(), e);
            return null;
        }
    }
}