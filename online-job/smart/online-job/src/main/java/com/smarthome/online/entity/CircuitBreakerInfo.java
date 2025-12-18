package com.smarthome.online.entity;

/**
 * 熔断接口信息
 */
public class CircuitBreakerInfo {
    private String interfaceId;   // 接口ID
    private Double errorRate;     // 错误率
    private String status;        // 状态
    private String lastUpdateTime; // 最后更新时间

    public CircuitBreakerInfo(String interfaceId, Double errorRate, String lastUpdateTime) {
        this.interfaceId = interfaceId;
        this.errorRate = errorRate;
        this.status = "CIRCUIT_BREAKER";
        this.lastUpdateTime = lastUpdateTime;
    }


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