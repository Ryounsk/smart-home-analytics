/**
 * 接口统计数据类型
 */
export interface InterfaceStats {
  interfaceId: string;
  interfaceName: string;
  totalCount: number;
  errorCount: number;
  errorRate: number;
  status: 'NORMAL' | 'WARNING' | 'CRITICAL';
  lastUpdated: number;
}

/**
 * 实时监控数据类型
 */
export interface RealTimeData {
  errorRateRanking: InterfaceStats[];
  circuitBreakerList: InterfaceStats[];
  normalInterfaceList: InterfaceStats[];
  totalInterfaces: number;
  totalErrors: number;
  averageErrorRate: number;
  lastUpdated: number;
}

/**
 * 离线分析数据类型
 */
export interface OfflineData {
  usageRanking: InterfaceStats[];
  optimizationSuggestions: InterfaceStats[];
  timeRange: {
    start: number;
    end: number;
  };
  totalUsage: number;
  top5UsagePercentage: number;
}

/**
 * API响应通用类型
 */
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  timestamp: number;
}

/**
 * 设备信息类型
 */
export interface DeviceInfo {
  deviceId: string;
  deviceName: string;
  deviceType: string;
  status: 'ONLINE' | 'OFFLINE' | 'ERROR';
  lastSeen: number;
  interfaces: InterfaceInfo[];
}

/**
 * 接口信息类型
 */
export interface InterfaceInfo {
  interfaceId: string;
  interfaceName: string;
  description: string;
  threshold: number;
  status: 'ENABLED' | 'DISABLED';
}

/**
 * 系统配置类型
 */
export interface SystemConfig {
  refreshInterval: number;
  errorThreshold: number;
  warningThreshold: number;
  dataRetentionDays: number;
  notificationEnabled: boolean;
}