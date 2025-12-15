/**
 * 格式化时间戳为日期字符串
 * @param timestamp 时间戳
 * @param format 格式字符串
 * @returns 格式化后的日期字符串
 */
export const formatDate = (timestamp: number, format = 'YYYY-MM-DD HH:mm:ss'): string => {
  const date = new Date(timestamp);
  
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  const second = String(date.getSeconds()).padStart(2, '0');
  
  return format
    .replace('YYYY', year.toString())
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hour)
    .replace('mm', minute)
    .replace('ss', second);
};

/**
 * 获取当前时间戳
 * @returns 当前时间戳
 */
export const getCurrentTimestamp = (): number => {
  return Date.now();
};

/**
 * 获取指定天数前的时间戳
 * @param days 天数
 * @returns 时间戳
 */
export const getDaysAgoTimestamp = (days: number): number => {
  return Date.now() - days * 24 * 60 * 60 * 1000;
};

/**
 * 计算两个时间戳之间的天数差
 * @param start 开始时间戳
 * @param end 结束时间戳
 * @returns 天数差
 */
export const getDaysBetween = (start: number, end: number): number => {
  const diff = end - start;
  return Math.floor(diff / (24 * 60 * 60 * 1000));
};

/**
 * 格式化时长
 * @param milliseconds 毫秒数
 * @returns 格式化后的时长字符串
 */
export const formatDuration = (milliseconds: number): string => {
  const seconds = Math.floor(milliseconds / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);
  
  if (days > 0) {
    return `${days}天${hours % 24}小时`;
  }
  if (hours > 0) {
    return `${hours}小时${minutes % 60}分钟`;
  }
  if (minutes > 0) {
    return `${minutes}分钟${seconds % 60}秒`;
  }
  return `${seconds}秒`;
};

/**
 * 获取本月第一天的时间戳
 * @returns 时间戳
 */
export const getMonthStartTimestamp = (): number => {
  const date = new Date();
  date.setDate(1);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
};

/**
 * 获取本月最后一天的时间戳
 * @returns 时间戳
 */
export const getMonthEndTimestamp = (): number => {
  const date = new Date();
  date.setMonth(date.getMonth() + 1);
  date.setDate(0);
  date.setHours(23, 59, 59, 999);
  return date.getTime();
};