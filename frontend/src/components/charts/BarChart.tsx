import React, { useEffect, useRef } from 'react';
import * as echarts from 'echarts';
import { Card, Spin, Alert } from 'antd';
import { InterfaceStats } from '../../types';
import { formatPercentage } from '../../utils/numberUtils';

interface BarChartProps {
  data: InterfaceStats[];
  title: string;
  xField: keyof InterfaceStats;
  yField: keyof InterfaceStats;
  loading?: boolean;
  error?: string;
  height?: number;
}

const BarChart: React.FC<BarChartProps> = ({
  data,
  title,
  xField,
  yField,
  loading = false,
  error,
  height = 400
}) => {
  const chartRef = useRef<HTMLDivElement>(null);
  const chartInstance = useRef<echarts.ECharts | null>(null);

  useEffect(() => {
    if (chartRef.current && !chartInstance.current) {
      chartInstance.current = echarts.init(chartRef.current);
    }

    return () => {
      if (chartInstance.current) {
        chartInstance.current.dispose();
        chartInstance.current = null;
      }
    };
  }, []);

  useEffect(() => {
    if (chartInstance.current && data && data.length > 0) {
      const chartData = data.map(item => ({
        name: item[xField] as string,
        value: item[yField] as number
      }));

      const option = {
        title: {
          text: title,
          left: 'center',
          textStyle: {
            fontSize: 16,
            fontWeight: 'bold'
          }
        },
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'shadow'
          },
          formatter: (params: any) => {
            const param = params[0];
            const item = data.find(d => d[xField] === param.name);
            if (!item) return '';
            
            return `
              <div style="padding: 8px;">
                <div><strong>${param.name}</strong></div>
                <div>总请求数: ${item.totalCount}</div>
                <div>错误数: ${item.errorCount}</div>
                <div>错误率: ${formatPercentage(item.errorRate)}</div>
                <div>状态: ${item.status === 'CRITICAL' ? '熔断中' : item.status === 'WARNING' ? '警告' : '正常'}</div>
              </div>
            `;
          }
        },
        grid: {
          left: '3%',
          right: '4%',
          bottom: '3%',
          containLabel: true
        },
        xAxis: {
          type: 'category',
          data: chartData.map(item => item.name),
          axisLabel: {
            rotate: 30,
            margin: 15
          }
        },
        yAxis: {
          type: 'value',
          axisLabel: {
            formatter: (value: number) => {
              if (yField === 'errorRate') {
                return `${(value * 100).toFixed(1)}%`;
              }
              return value;
            }
          }
        },
        series: [
          {
            name: yField === 'errorRate' ? '错误率' : yField === 'totalCount' ? '总请求数' : '使用次数',
            type: 'bar',
            data: chartData,
            itemStyle: {
              color: (params: any) => {
                const item = data.find(d => d[xField] === params.name);
                if (!item) return '#1890ff';
                
                if (yField === 'errorRate') {
                  if (item.errorRate >= 0.05) return '#ff4d4f'; // 红色 - 熔断
                  if (item.errorRate >= 0.02) return '#faad14'; // 橙色 - 警告
                  return '#52c41a'; // 绿色 - 正常
                }
                return '#1890ff'; // 蓝色 - 默认
              }
            },
            label: {
              show: true,
              position: 'top',
              formatter: (params: any) => {
                if (yField === 'errorRate') {
                  return `${(params.value * 100).toFixed(1)}%`;
                }
                return params.value;
              }
            }
          }
        ]
      };

      chartInstance.current.setOption(option);
    }
  }, [data, xField, yField, title]);

  useEffect(() => {
    const handleResize = () => {
      if (chartInstance.current) {
        chartInstance.current.resize();
      }
    };

    window.addEventListener('resize', handleResize);
    return () => {
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  if (loading) {
    return (
      <Card>
        <div style={{ height, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Spin tip="图表加载中..." />
        </div>
      </Card>
    );
  }

  if (error) {
    return (
      <Card>
        <Alert message="图表加载失败" description={error} type="error" showIcon />
      </Card>
    );
  }

  if (!data || data.length === 0) {
    return (
      <Card>
        <Alert message="暂无数据" description="没有可用的统计数据" type="info" showIcon />
      </Card>
    );
  }

  return (
    <Card>
      <div 
        ref={chartRef} 
        style={{ height, width: '100%' }} 
      />
    </Card>
  );
};

export default BarChart;