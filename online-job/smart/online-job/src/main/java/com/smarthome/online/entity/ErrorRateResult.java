package com.smarthome.online.entity;

import java.io.Serializable;

/**
 * 错误率结果实体类
 * 必须添加无参构造方法和getter方法，支持Jackson序列化/反序列化
 */
public class ErrorRateResult implements Serializable {
    // 字段
    private String interfaceId;
    private double errorRate;
    private long totalCount;
    private long errorCount;
    private String lastUpdateTime;

    public ErrorRateResult() {
    }

    public ErrorRateResult(String interfaceId, double errorRate, long totalCount, long errorCount, String lastUpdateTime) {
        this.interfaceId = interfaceId;
        this.errorRate = errorRate;
        this.totalCount = totalCount;
        this.errorCount = errorCount;
        this.lastUpdateTime = lastUpdateTime;
    }

    public String getInterfaceId() {
        return interfaceId;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public String getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setInterfaceId(String interfaceId) {
        this.interfaceId = interfaceId;
    }

    public void setErrorRate(double errorRate) {
        this.errorRate = errorRate;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public void setErrorCount(long errorCount) {
        this.errorCount = errorCount;
    }

    public void setLastUpdateTime(String lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }
}