import axios, { AxiosRequestConfig, AxiosResponse, AxiosError } from 'axios';
import { message } from 'antd';
import { ApiResponse } from '../types';

// 创建axios实例
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截器
api.interceptors.request.use(
  (config: AxiosRequestConfig) => {
    // 可以在这里添加认证token
    // const token = localStorage.getItem('token');
    // if (token) {
    //   config.headers.Authorization = `Bearer ${token}`;
    // }
    return config;
  },
  (error: AxiosError) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
api.interceptors.response.use(
  (response: AxiosResponse<ApiResponse<any>>) => {
    return response.data;
  },
  (error: AxiosError<ApiResponse<any>>) => {
    const errorMessage = error.response?.data?.message || '请求失败，请稍后重试';
    message.error(errorMessage);
    return Promise.reject(error);
  }
);

/**
 * GET请求
 * @param url 请求URL
 * @param params 请求参数
 * @returns Promise
 */
export const get = async <T>(url: string, params?: any): Promise<T> => {
  const response = await api.get<ApiResponse<T>>(url, { params });
  return response.data;
};

/**
 * POST请求
 * @param url 请求URL
 * @param data 请求数据
 * @returns Promise
 */
export const post = async <T>(url: string, data?: any): Promise<T> => {
  const response = await api.post<ApiResponse<T>>(url, data);
  return response.data;
};

/**
 * PUT请求
 * @param url 请求URL
 * @param data 请求数据
 * @returns Promise
 */
export const put = async <T>(url: string, data?: any): Promise<T> => {
  const response = await api.put<ApiResponse<T>>(url, data);
  return response.data;
};

/**
 * DELETE请求
 * @param url 请求URL
 * @returns Promise
 */
export const del = async <T>(url: string): Promise<T> => {
  const response = await api.delete<ApiResponse<T>>(url);
  return response.data;
};

export default api;