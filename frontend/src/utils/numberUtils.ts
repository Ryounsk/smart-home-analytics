/**
 * 格式化数字为千分位
 * @param num 数字
 * @param decimal 小数位数
 * @returns 格式化后的字符串
 */
export const formatNumber = (num: number, decimal = 2): string => {
  if (isNaN(num)) return '0';
  
  const fixedNum = num.toFixed(decimal);
  const parts = fixedNum.split('.');
  parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return parts.join('.');
};

/**
 * 格式化百分比
 * @param num 数字
 * @param decimal 小数位数
 * @returns 格式化后的百分比字符串
 */
export const formatPercentage = (num: number, decimal = 2): string => {
  if (isNaN(num)) return '0%';
  return `${(num * 100).toFixed(decimal)}%`;
};

/**
 * 计算错误率
 * @param errorCount 错误数量
 * @param totalCount 总数量
 * @returns 错误率
 */
export const calculateErrorRate = (errorCount: number, totalCount: number): number => {
  if (totalCount === 0) return 0;
  return errorCount / totalCount;
};

/**
 * 格式化字节数
 * @param bytes 字节数
 * @param decimal 小数位数
 * @returns 格式化后的字符串
 */
export const formatBytes = (bytes: number, decimal = 2): string => {
  if (bytes === 0) return '0 Bytes';
  
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(decimal))} ${sizes[i]}`;
};

/**
 * 生成随机数
 * @param min 最小值
 * @param max 最大值
 * @returns 随机数
 */
export const randomNumber = (min: number, max: number): number => {
  return Math.floor(Math.random() * (max - min + 1)) + min;
};

/**
 * 生成随机颜色
 * @returns 十六进制颜色
 */
export const randomColor = (): string => {
  return `#${Math.floor(Math.random() * 16777215).toString(16)}`;
};

/**
 * 安全除法
 * @param numerator 分子
 * @param denominator 分母
 * @param defaultValue 默认值
 * @returns 结果
 */
export const safeDivide = (numerator: number, denominator: number, defaultValue = 0): number => {
  if (denominator === 0) return defaultValue;
  return numerator / denominator;
};

/**
 * 计算两个数的百分比差异
 * @param current 当前值
 * @param previous 之前值
 * @returns 百分比差异
 */
export const calculatePercentageChange = (current: number, previous: number): number => {
  if (previous === 0) return current > 0 ? 100 : 0;
  return ((current - previous) / previous) * 100;
};