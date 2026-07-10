package com.smarthome.online.entity;

/**
 * 熔断接口信息（补充apiName字段，匹配Apifox返回格式）
 * 已添加无参构造方法，支持JSON反序列化
 */
public class CircuitBreakerInfo {
    private String interfaceId;   // 接口ID（统一格式：interface_xxx）
    private String apiName;       // 补充：原始接口名称
    private Double errorRate;     // 错误率
    private String status;        // 状态（固定为CIRCUIT_BREAKER）
    private String lastUpdateTime; // 最后更新时间

    // ************************** 新增：无参构造方法（Jackson反序列化必须）**************************
    public CircuitBreakerInfo() {
        // 可选：可以在这里给status设置默认值，和原有逻辑保持一致
        this.status = "CIRCUIT_BREAKER";
    }

    // 新增：包含apiName的构造函数（适配Flink作业数据写入）
    public CircuitBreakerInfo(String interfaceId, String apiName, Double errorRate, String lastUpdateTime) {
        this.interfaceId = interfaceId;
        this.apiName = apiName;
        this.errorRate = errorRate;
        this.status = "CIRCUIT_BREAKER";
        this.lastUpdateTime = lastUpdateTime;
    }

    // 保留原构造函数（兼容旧逻辑）
    public CircuitBreakerInfo(String interfaceId, Double errorRate, String lastUpdateTime) {
        this.interfaceId = interfaceId;
        this.errorRate = errorRate;
        this.status = "CIRCUIT_BREAKER";
        this.lastUpdateTime = lastUpdateTime;
    }

    // 新增apiName的getter（后端接口查询Redis时需获取该字段）
    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    // 原有getter/setter保持不变
    public String getInterfaceId() {
        return interfaceId;
    }

    public void setInterfaceId(String interfaceId) {
        this.interfaceId = interfaceId;
    }

    public Double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(Double errorRate) {
        this.errorRate = errorRate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(String lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }
}