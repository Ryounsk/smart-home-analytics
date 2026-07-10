package com.smarthome.online.entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 设备接口日志实体类
 */
public class DeviceInterfaceLog {
    private String logId;
    private String apiName;
    private String apiResponseTime;
    private String apiCallStatus;
    private LocalDateTime apiCallDate;

    // 判断调用是否失败
    public boolean isFail() {
        return "fail".equalsIgnoreCase(apiCallStatus);
    }

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(apiCallStatus);
    }

    // 处理接口耗时
    public Double parseDuration() {
        if (apiResponseTime == null || apiResponseTime.isEmpty()) {
            return 0.0;
        }
        try {
            // 剥离ms后缀，处理纯数字和带ms的情况
            String durationStr = apiResponseTime.replace("ms", "").trim();
            return Double.parseDouble(durationStr);
        } catch (NumberFormatException e) {
            return 0.0; // 异常情况默认0毫秒
        }
    }

    public void parseCallDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            this.apiCallDate = LocalDateTime.now();
            return;
        }
        // 支持标准DateTime格式（yyyy-MM-dd HH:mm:ss）和原有的yyyy-MM-dd-HH:mm格式
        DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm")
        };
        for (DateTimeFormatter formatter : formatters) {
            try {
                this.apiCallDate = LocalDateTime.parse(dateStr, formatter);
                return;
            } catch (DateTimeParseException e) {
                continue; // 格式不匹配则尝试下一个
            }
        }
        // 所有格式都不匹配时默认当前时间
        this.apiCallDate = LocalDateTime.now();
    }

    public String getLogId() {
        return logId;
    }

    public void setLogId(String logId) {
        this.logId = logId;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getApiResponseTime() {
        return apiResponseTime;
    }

    public void setApiResponseTime(String apiResponseTime) {
        this.apiResponseTime = apiResponseTime;
    }

    public String getApiCallStatus() {
        return apiCallStatus;
    }

    public void setApiCallStatus(String apiCallStatus) {
        this.apiCallStatus = apiCallStatus;
    }

    public LocalDateTime getApiCallDate() {
        return apiCallDate;
    }

    public void setApiCallDate(LocalDateTime apiCallDate) {
        this.apiCallDate = apiCallDate;
    }
}