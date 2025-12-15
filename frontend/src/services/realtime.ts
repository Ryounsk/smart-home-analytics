import { get } from './api';
import { RealTimeData, InterfaceStats } from '../types';

/**
 * 获取实时监控数据
 * @returns 实时监控数据
 */
export const getRealTimeData = async (): Promise<RealTimeData> => {
  return get<RealTimeData>('/realtime/data');
};

/**
 * 获取错误率排行榜
 * @returns 错误率排行榜数据
 */
export const getErrorRateRanking = async (): Promise<InterfaceStats[]> => {
  return get<InterfaceStats[]>('/realtime/error-rate-ranking');
};

/**
 * 获取熔断接口列表
 * @returns 熔断接口列表
 */
export const getCircuitBreakerList = async (): Promise<InterfaceStats[]> => {
  return get<InterfaceStats[]>('/realtime/circuit-breaker');
};

/**
 * 获取正常接口列表
 * @returns 正常接口列表
 */
export const getNormalInterfaceList = async (): Promise<InterfaceStats[]> => {
  return get<InterfaceStats[]>('/realtime/normal-interfaces');
};

/**
 * 获取实时告警
 * @returns 实时告警列表
 */
export const getRealTimeAlerts = async (): Promise<any[]> => {
  return get<any[]>('/realtime/alerts');
};

/**
 * 重置熔断状态
 * @param interfaceId 接口ID
 * @returns 操作结果
 */
export const resetCircuitBreaker = async (interfaceId: string): Promise<boolean> => {
  return get<boolean>(`/realtime/reset-circuit-breaker?interfaceId=${interfaceId}`);
};