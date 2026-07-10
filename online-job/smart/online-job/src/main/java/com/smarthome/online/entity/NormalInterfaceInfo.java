package com.smarthome.online.entity;

/**
 * 健康接口信息（对应接口3：错误率<5%的接口列表）
 */
public class NormalInterfaceInfo {
    private String interfaceId;   // 接口ID
    private Double errorRate;     // 错误率
    private String status;        // 状态（固定为NORMAL）
    private Long totalCount;      // 总调用次数

    public NormalInterfaceInfo(String interfaceId, Double errorRate, Long totalCount) {
        this.interfaceId = interfaceId;
        this.errorRate = errorRate;
        this.status = "NORMAL";
        this.totalCount = totalCount;
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

    public Long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }
}