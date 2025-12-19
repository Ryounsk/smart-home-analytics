package com.smarthome.online;

import com.smarthome.online.entity.CircuitBreakerInfo;
import com.smarthome.online.entity.ErrorRateResult;
import com.smarthome.online.entity.NormalInterfaceInfo;
import com.smarthome.online.util.JsonUtil;
import com.smarthome.online.util.RedisUtil;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 独立的API服务器（适配方案一：实时数据接口直接读ZSet的JSON member）
 * 熔断/正常接口：直接读对应Redis Hash的完整JSON数据
 */
public class ApiServer {

    private static final Logger logger = LoggerFactory.getLogger(ApiServer.class);
    private static final int PORT = 8080; // 若冲突可改为8082等
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // Redis核心配置（方案一：ZSet直接存JSON）
    private static final String REDIS_RANKING_KEY = "interface_error_rate_ranking";
    private static final int INTERFACE_COUNT = 15;
    private static final double BREAKER_THRESHOLD = 0.05;
    // 熔断/正常接口的Redis Hash Key
    private static final String REDIS_CIRCUIT_BREAKER_KEY = "interface_circuit_breaker";
    private static final String REDIS_NORMAL_INTERFACE_KEY = "interface_normal_interface";

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
     * 方案一：直接从ZSet读取JSON member，取前15条
     */
    static class RealtimeDataHandler implements HttpHandler {
        private static final Logger logger = LoggerFactory.getLogger(RealtimeDataHandler.class);

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                try {
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
         * 方案一：直接从ZSet读取JSON member（降序取前15条）
         */
        private List<Map<String, Object>> getRealDataFromRedis() {
            List<Map<String, Object>> interfaces = new ArrayList<>();
            try (Jedis jedis = RedisUtil.getJedis()) {
                // 步骤1：从ZSet降序取前15条member（JSON字符串）
                // zrevrange：从大到小取，参数：key, 起始索引, 结束索引
                List<String> jsonList = jedis.zrevrange(REDIS_RANKING_KEY, 0, INTERFACE_COUNT - 1);
                if (jsonList == null || jsonList.isEmpty()) {
                    logger.warn("Redis中未读取到{}键的数据", REDIS_RANKING_KEY);
                    return interfaces;
                }

                // 步骤2：解析JSON为对象，转为Map
                for (String json : jsonList) {
                    if (json == null || json.isEmpty()) {
                        logger.warn("ZSet中的JSON数据为空，跳过");
                        continue;
                    }

                    ErrorRateResult result = JsonUtil.fromJson(json, ErrorRateResult.class);
                    if (result == null) {
                        logger.warn("JSON解析失败，跳过：{}", json);
                        continue;
                    }

                    Map<String, Object> interfaceInfo = new HashMap<>();
                    interfaceInfo.put("interfaceId", result.getInterfaceId());
                    interfaceInfo.put("apiName", result.getInterfaceId().replace("interface_", ""));
                    interfaceInfo.put("status", result.getErrorRate() >= BREAKER_THRESHOLD ? "CIRCUIT_BREAKER" : "NORMAL");
                    interfaceInfo.put("errorRate", String.format("%.4f", result.getErrorRate()));
                    interfaceInfo.put("totalCount", result.getTotalCount());
                    interfaceInfo.put("lastUpdateTime", result.getLastUpdateTime());
                    interfaces.add(interfaceInfo);
                }
            } catch (Exception e) {
                logger.error("从Redis读取数据失败", e);
            }

            return interfaces;
        }
    }

    /**
     * 路由2：熔断接口列表（/api/realtime/circuit-breaker）
     * 直接从Redis Hash读取熔断接口数据
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

                    // 直接从熔断接口的Redis Hash中读取数据
                    try (Jedis jedis = RedisUtil.getJedis()) {
                        Map<String, String> breakerMap = jedis.hgetAll(REDIS_CIRCUIT_BREAKER_KEY);
                        if (breakerMap != null && !breakerMap.isEmpty()) {
                            for (Map.Entry<String, String> entry : breakerMap.entrySet()) {
                                String interfaceId = entry.getKey();
                                String json = entry.getValue();

                                if (json == null || json.isEmpty()) {
                                    logger.warn("熔断接口{}的JSON数据为空，跳过", interfaceId);
                                    continue;
                                }

                                CircuitBreakerInfo breakerInfo = JsonUtil.fromJson(json, CircuitBreakerInfo.class);
                                if (breakerInfo != null) {
                                    Map<String, Object> circuitBreaker = new HashMap<>();
                                    circuitBreaker.put("interfaceId", breakerInfo.getInterfaceId());
                                    circuitBreaker.put("apiName", breakerInfo.getApiName());
                                    circuitBreaker.put("errorRate", String.format("%.4f", breakerInfo.getErrorRate()));
                                    circuitBreaker.put("status", "CIRCUIT_BREAKER");
                                    circuitBreaker.put("lastUpdateTime", breakerInfo.getLastUpdateTime());
                                    circuitBreakers.add(circuitBreaker);
                                } else {
                                    logger.warn("熔断接口{}的JSON解析失败，跳过", interfaceId);
                                }
                            }
                        } else {
                            logger.warn("Redis中未读取到{}键的数据", REDIS_CIRCUIT_BREAKER_KEY);
                        }
                    } catch (Exception e) {
                        logger.error("从Redis读取熔断接口数据失败", e);
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
     * 直接从Redis Hash读取正常接口数据
     */
    static class NormalInterfacesHandler implements HttpHandler {
        private static final Logger logger = LoggerFactory.getLogger(NormalInterfacesHandler.class);

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    List<Map<String, Object>> normalInterfaces = new ArrayList<>();

                    // 直接从正常接口的Redis Hash中读取数据
                    try (Jedis jedis = RedisUtil.getJedis()) {
                        Map<String, String> normalMap = jedis.hgetAll(REDIS_NORMAL_INTERFACE_KEY);
                        if (normalMap != null && !normalMap.isEmpty()) {
                            for (Map.Entry<String, String> entry : normalMap.entrySet()) {
                                String interfaceId = entry.getKey();
                                String json = entry.getValue();

                                if (json == null || json.isEmpty()) {
                                    logger.warn("正常接口{}的JSON数据为空，跳过", interfaceId);
                                    continue;
                                }

                                NormalInterfaceInfo normalInfo = JsonUtil.fromJson(json, NormalInterfaceInfo.class);
                                if (normalInfo != null) {
                                    Map<String, Object> normalInterface = new HashMap<>();
                                    normalInterface.put("interfaceId", normalInfo.getInterfaceId());
                                    normalInterface.put("apiName", normalInfo.getInterfaceId().replace("interface_", ""));
                                    normalInterface.put("errorRate", String.format("%.4f", normalInfo.getErrorRate()));
                                    normalInterface.put("status", "NORMAL");
                                    normalInterface.put("totalCount", normalInfo.getTotalCount());
                                    normalInterfaces.add(normalInterface);
                                } else {
                                    logger.warn("正常接口{}的JSON解析失败，跳过", interfaceId);
                                }
                            }
                        } else {
                            logger.warn("Redis中未读取到{}键的数据", REDIS_NORMAL_INTERFACE_KEY);
                        }
                    } catch (Exception e) {
                        logger.error("从Redis读取正常接口数据失败", e);
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
     * 发送正常响应（支持跨域，统一JSON格式）
     */
    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*"); // 解决跨域问题
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    /**
     * 发送错误响应（统一错误格式）
     */
    private static void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("code", statusCode);
        errorResponse.put("message", message);
        errorResponse.put("timestamp", LocalDateTime.now().format(formatter));

        sendResponse(exchange, statusCode, JsonUtil.toJson(errorResponse));
    }
}