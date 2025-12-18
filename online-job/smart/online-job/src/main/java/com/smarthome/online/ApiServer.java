package com.smarthome.online;

import com.smarthome.online.entity.ErrorRateResult;
import com.smarthome.online.util.JsonUtil;
import com.smarthome.online.util.RedisUtil;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 独立的API服务器（仅从hadoop103的Redis读取真实数据，移除模拟数据逻辑）
 */
public class ApiServer {

    private static final Logger logger = LoggerFactory.getLogger(ApiServer.class);
    private static final int PORT = 8080; // 若冲突可改为8082等
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // Redis配置
    private static final String REDIS_RANKING_KEY = "interface_error_rate_ranking";
    private static final int INTERFACE_COUNT = 15;
    private static final double BREAKER_THRESHOLD = 0.05;

    public static void main(String[] args) throws IOException {
        // 初始化HTTP服务
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // 注册路由
        server.createContext("/api/realtime/data", new RealtimeDataHandler());
        server.createContext("/api/realtime/circuit-breaker", new CircuitBreakerHandler());
        server.createContext("/api/realtime/normal-interfaces", new NormalInterfacesHandler());

        server.setExecutor(null); // 使用默认线程池
        server.start();

        // 日志打印真实访问地址
        logger.info("API服务器启动成功，监听端口: {}", PORT);
        logger.info("可用接口（本地访问）:");
        logger.info("  GET http://localhost:{}/api/realtime/data", PORT);
        logger.info("  GET http://localhost:{}/api/realtime/circuit-breaker", PORT);
        logger.info("  GET http://localhost:{}/api/realtime/normal-interfaces", PORT);
        logger.info("可用接口（远程访问，替换为hadoop102的IP）:");
        logger.info("  GET http://hadoop102:{}/api/realtime/data", PORT);
    }

    /**
     * 路由1：实时数据接口（/api/realtime/data）
     * 仅从Redis读取真实数据，取前15条
     */
    static class RealtimeDataHandler implements HttpHandler {
        private static final Logger logger = LoggerFactory.getLogger(RealtimeDataHandler.class);

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    // 直接从Redis读取真实数据
                    List<Map<String, Object>> interfaces = getRealDataFromRedis();

                    // 构造响应
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 200);
                    response.put("message", "success");
                    response.put("data", interfaces);
                    response.put("timestamp", LocalDateTime.now().format(formatter));

                    logger.debug("返回实时数据: {}条记录", interfaces.size());
                    sendResponse(exchange, 200, JsonUtil.toJson(response));
                } catch (Exception e) {
                    logger.error("获取实时数据失败", e);
                    sendErrorResponse(exchange, 500, "服务器内部错误");
                }
            } else {
                sendErrorResponse(exchange, 405, "不支持的方法");
            }
        }

        /**
         * 从hadoop103的Redis读取数据
         */
        private List<Map<String, Object>> getRealDataFromRedis() {
            List<Map<String, Object>> interfaces = new ArrayList<>();
            // 从Redis读取有序集合（降序，取前15条）
            List<String> jsonList = RedisUtil.zrevrange(REDIS_RANKING_KEY, 0, INTERFACE_COUNT - 1);

            // 空值处理：若Redis无数据，直接返回空列表
            if (jsonList == null || jsonList.isEmpty()) {
                logger.warn("Redis中未读取到{}键的数据", REDIS_RANKING_KEY);
                return interfaces;
            }

            for (String json : jsonList) {
                // 反序列化为ErrorRateResult，再转为Map保持格式兼容
                ErrorRateResult result = JsonUtil.fromJson(json, ErrorRateResult.class);
                Map<String, Object> interfaceInfo = new HashMap<>();
                interfaceInfo.put("interfaceId", result.getInterfaceId());
                interfaceInfo.put("apiName", result.getInterfaceId().replace("interface_", "")); // 从interfaceId提取apiName
                interfaceInfo.put("status", result.getErrorRate() >= BREAKER_THRESHOLD ? "CIRCUIT_BREAKER" : "NORMAL");
                interfaceInfo.put("errorRate", String.format("%.4f", result.getErrorRate()));
                interfaceInfo.put("totalCount", result.getTotalCount());
                interfaceInfo.put("lastUpdateTime", result.getLastUpdateTime());
                interfaces.add(interfaceInfo);
            }

            return interfaces;
        }
    }

    /**
     * 路由2：熔断接口列表（/api/realtime/circuit-breaker）
     * 仅从Redis读取数据，筛选熔断接口
     */
    static class CircuitBreakerHandler implements HttpHandler {
        private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerHandler.class);

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    List<Map<String, Object>> circuitBreakers = new ArrayList<>();
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 200);
                    response.put("timestamp", LocalDateTime.now().format(formatter));

                    // 从Redis读取所有数据，筛选熔断接口
                    List<String> jsonList = RedisUtil.zrevrange(REDIS_RANKING_KEY, 0, -1);
                    if (jsonList != null && !jsonList.isEmpty()) {
                        for (String json : jsonList) {
                            ErrorRateResult result = JsonUtil.fromJson(json, ErrorRateResult.class);
                            if (result.getErrorRate() >= BREAKER_THRESHOLD) {
                                Map<String, Object> circuitBreaker = new HashMap<>();
                                circuitBreaker.put("interfaceId", result.getInterfaceId());
                                circuitBreaker.put("apiName", result.getInterfaceId().replace("interface_", ""));
                                circuitBreaker.put("errorRate", result.getErrorRate());
                                circuitBreaker.put("status", "CIRCUIT_BREAKER");
                                circuitBreaker.put("lastUpdateTime", result.getLastUpdateTime());
                                circuitBreakers.add(circuitBreaker);
                            }
                        }
                    } else {
                        logger.warn("Redis中未读取到{}键的数据", REDIS_RANKING_KEY);
                    }

                    // 构造响应
                    if (!circuitBreakers.isEmpty()) {
                        response.put("message", "success");
                        response.put("data", circuitBreakers);
                        logger.info("发现熔断接口: {}个", circuitBreakers.size());
                    } else {
                        response.put("message", "未发现熔断接口");
                        response.put("data", null);
                        logger.info("未发现熔断接口");
                    }

                    sendResponse(exchange, 200, JsonUtil.toJson(response));
                } catch (Exception e) {
                    logger.error("获取熔断接口列表失败", e);
                    sendErrorResponse(exchange, 500, "服务器内部错误");
                }
            } else {
                sendErrorResponse(exchange, 405, "不支持的方法");
            }
        }
    }

    /**
     * 路由3：正常接口列表（/api/realtime/normal-interfaces）
     * 仅从Redis读取数据，筛选正常接口
     */
    static class NormalInterfacesHandler implements HttpHandler {
        private static final Logger logger = LoggerFactory.getLogger(NormalInterfacesHandler.class);

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    List<Map<String, Object>> normalInterfaces = new ArrayList<>();

                    // 从Redis读取所有数据，筛选正常接口
                    List<String> jsonList = RedisUtil.zrevrange(REDIS_RANKING_KEY, 0, -1);
                    if (jsonList != null && !jsonList.isEmpty()) {
                        for (String json : jsonList) {
                            ErrorRateResult result = JsonUtil.fromJson(json, ErrorRateResult.class);
                            if (result.getErrorRate() < BREAKER_THRESHOLD) {
                                Map<String, Object> normalInterface = new HashMap<>();
                                normalInterface.put("interfaceId", result.getInterfaceId());
                                normalInterface.put("apiName", result.getInterfaceId().replace("interface_", ""));
                                normalInterface.put("errorRate", String.format("%.4f", result.getErrorRate()));
                                normalInterface.put("status", "NORMAL");
                                normalInterface.put("totalCount", result.getTotalCount());
                                normalInterfaces.add(normalInterface);
                            }
                        }
                    } else {
                        logger.warn("Redis中未读取到{}键的数据", REDIS_RANKING_KEY);
                    }

                    // 构造响应
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", 200);
                    response.put("message", "success");
                    response.put("data", normalInterfaces);
                    response.put("timestamp", LocalDateTime.now().format(formatter));

                    logger.debug("返回正常接口数据: {}条记录", normalInterfaces.size());
                    sendResponse(exchange, 200, JsonUtil.toJson(response));
                } catch (Exception e) {
                    logger.error("获取正常接口列表失败", e);
                    sendErrorResponse(exchange, 500, "服务器内部错误");
                }
            } else {
                sendErrorResponse(exchange, 405, "不支持的方法");
            }
        }
    }

    /**
     * 发送正常响应
     */
    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*"); // 解决跨域
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    /**
     * 发送错误响应
     */
    private static void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("code", statusCode);
        errorResponse.put("message", message);
        errorResponse.put("timestamp", LocalDateTime.now().format(formatter));

        sendResponse(exchange, statusCode, JsonUtil.toJson(errorResponse));
    }
}