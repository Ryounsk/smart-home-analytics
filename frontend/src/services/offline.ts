import { get, post } from './api';
import { OfflineData, InterfaceStats } from '../types';

/**
 * 获取离线分析数据
 * @param start 开始时间
 * @param end 结束时间
 * @returns 离线分析数据
 */
export const getOfflineData = async (start?: number, end?: number): Promise<OfflineData> => {
  const params = {
    start,
    end
  };
  return get<OfflineData>('/offline/data', params);
};

/**
 * 获取接口使用率排行榜
 * @param start 开始时间
 * @param end 结束时间
 * @returns 使用率排行榜
 */
export const getUsageRanking = async (start?: number, end?: number): Promise<InterfaceStats[]> => {
  const params = {
    start,
    end
  };
  return get<InterfaceStats[]>('/offline/usage-ranking', params);
};

/**
 * 获取优化建议列表
 * @param start 开始时间
 * @param end 结束时间
 * @returns 优化建议列表
 */
export const getOptimizationSuggestions = async (start?: number, end?: number): Promise<InterfaceStats[]> => {
  const params = {
    start,
    end
  };
  return get<InterfaceStats[]>('/offline/optimization-suggestions', params);
};

/**
 * 触发离线分析任务
 * @returns 任务ID
 */
export const triggerOfflineAnalysis = async (): Promise<string> => {
  return post<string>('/offline/trigger-analysis');
};

/**
 * 获取分析任务状态
 * @param taskId 任务ID
 * @returns 任务状态
 */
export const getTaskStatus = async (taskId: string): Promise<any> => {
  return get<any>(`/offline/task-status?taskId=${taskId}`);
};

/**
 * 获取历史分析报告
 * @param reportId 报告ID
 * @returns 分析报告
 */
export const getAnalysisReport = async (reportId: string): Promise<any> => {
  return get<any>(`/offline/report?reportId=${reportId}`);
};