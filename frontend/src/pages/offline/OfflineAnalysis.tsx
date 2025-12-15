import React, { useEffect, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { 
  Layout, Card, Button, Typography, Row, Col, Table, 
  Tag, Progress, DatePicker, Space, Statistic, message,
  Descriptions, Badge, Divider, Popover
} from 'antd';
import { 
  BarChartOutlined, CalendarOutlined, 
  ReloadOutlined, WarningOutlined, ClockCircleOutlined
} from '@ant-design/icons';
import { RootState, AppDispatch } from '../../store';
import { 
  fetchOfflineData, 
  fetchUsageRanking, 
  fetchOptimizationSuggestions,
  startOfflineAnalysis,
  checkTaskStatus,
  setTimeRange
} from '../../store/slices/offlineSlice';
import BarChart from '../../components/charts/BarChart';
import { formatDate, getMonthStartTimestamp, getMonthEndTimestamp } from '../../utils/dateUtils';
import { formatNumber } from '../../utils/numberUtils';
import { InterfaceStats } from '../../types';

const { Title, Text } = Typography;
const { Content } = Layout;
const { RangePicker } = DatePicker;

const OfflineAnalysis: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>();
  const { 
    data, 
    usageRanking, 
    optimizationSuggestions, 
    loading, 
    error,
    taskStatus,
    timeRange
  } = useSelector((state: RootState) => state.offline);
  
  const [dateRange, setDateRange] = useState<[number, number]>([
    getMonthStartTimestamp(),
    getMonthEndTimestamp()
  ]);
  const [taskPolling, setTaskPolling] = useState<NodeJS.Timeout | null>(null);

  // 表格列定义
  const usageRankingColumns = [
    {
      title: '排名',
      key: 'rank',
      render: (_, __, index: number) => (
        <Tag color={index < 3 ? 'gold' : index < 10 ? 'silver' : 'bronze'}>
          {index + 1}
        </Tag>
      ),
    },
    {
      title: '接口ID',
      dataIndex: 'interfaceId',
      key: 'interfaceId',
    },
    {
      title: '接口名称',
      dataIndex: 'interfaceName',
      key: 'interfaceName',
    },
    {
      title: '使用次数',
      dataIndex: 'totalCount',
      key: 'totalCount',
      render: (text: number) => <Text strong>{formatNumber(text)}</Text>,
    },
    {
      title: '占比',
      dataIndex: 'totalCount',
      key: 'percentage',
      render: (count: number) => {
        const percentage = data?.totalUsage ? (count / data.totalUsage) * 100 : 0;
        return (
          <div>
            <Progress 
              percent={percentage.toFixed(1)} 
              size="small" 
              status="active" 
            />
            <span style={{ marginLeft: 8 }}>{percentage.toFixed(1)}%</span>
          </div>
        );
      },
    },
  ];

  const optimizationColumns = [
    {
      title: '排名',
      key: 'rank',
      render: (_, __, index: number) => (
        <Tag color="red">TOP {index + 1}</Tag>
      ),
    },
    {
      title: '接口ID',
      dataIndex: 'interfaceId',
      key: 'interfaceId',
    },
    {
      title: '接口名称',
      dataIndex: 'interfaceName',
      key: 'interfaceName',
    },
    {
      title: '使用次数',
      dataIndex: 'totalCount',
      key: 'totalCount',
      render: (text: number) => <Text strong>{formatNumber(text)}</Text>,
    },
    {
      title: '优化建议',
      key: 'suggestion',
      render: () => (
        <Popover 
          content={
            <div style={{ width: 200 }}>
              <p>1. 增加缓存层减少数据库压力</p>
              <p>2. 优化SQL查询语句</p>
              <p>3. 考虑接口拆分或异步处理</p>
              <p>4. 增加监控告警机制</p>
            </div>
          }
          title="优化建议"
          trigger="hover"
        >
          <Button type="link" icon={<WarningOutlined />}>
            查看建议
          </Button>
        </Popover>
      ),
    },
  ];

  // 处理日期范围变化
  const handleDateChange = (dates: any, dateStrings: string[]) => {
    if (dates && dates.length === 2) {
      const start = dates[0].valueOf();
      const end = dates[1].valueOf();
      setDateRange([start, end]);
      dispatch(setTimeRange({ start, end }));
      fetchData(start, end);
    }
  };

  // 获取数据
  const fetchData = (start?: number, end?: number) => {
    dispatch(fetchOfflineData({ start, end }));
  };

  // 触发离线分析
  const handleTriggerAnalysis = () => {
    dispatch(startOfflineAnalysis())
      .unwrap()
      .then((taskId) => {
        message.success(`离线分析已触发，任务ID: ${taskId}`);
        
        // 轮询任务状态
        const interval = setInterval(() => {
          dispatch(checkTaskStatus(taskId))
            .unwrap()
            .then((status) => {
              if (status.status === 'COMPLETED') {
                message.success('离线分析完成');
                fetchData();
                if (taskPolling) clearInterval(taskPolling);
                setTaskPolling(null);
              } else if (status.status === 'FAILED') {
                message.error('离线分析失败');
                if (taskPolling) clearInterval(taskPolling);
                setTaskPolling(null);
              }
            });
        }, 5000);
        
        setTaskPolling(interval);
      })
      .catch((err) => {
        message.error(`触发失败: ${err}`);
      });
  };

  // 组件挂载时获取数据
  useEffect(() => {
    fetchData(timeRange.start, timeRange.end);
    
    return () => {
      if (taskPolling) {
        clearInterval(taskPolling);
      }
    };
  }, [dispatch]);

  return (
    <Content>
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <Title level={2}>离线分析面板</Title>
            <Space>
              <RangePicker
                value={[
                  dateRange[0] ? new Date(dateRange[0]) : null,
                  dateRange[1] ? new Date(dateRange[1]) : null
                ]}
                onChange={handleDateChange}
                format="YYYY-MM-DD"
                style={{ width: 300 }}
              />
              <Button 
                type="primary" 
                icon={<BarChartOutlined />} 
                onClick={() => fetchData(dateRange[0], dateRange[1])}
                loading={loading}
              >
                查询数据
              </Button>
              <Button 
                type="default" 
                icon={<CalendarOutlined />} 
                onClick={handleTriggerAnalysis}
                disabled={taskStatus.status === 'RUNNING'}
              >
                {taskStatus.status === 'RUNNING' ? '分析中...' : '触发分析'}
              </Button>
            </Space>
          </div>
        </Col>
      </Row>

      {/* 任务状态 */}
      {taskStatus.status && (
        <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
          <Col span={24}>
            <Card bordered={false}>
              <Descriptions title="任务状态" bordered>
                <Descriptions.Item label="任务ID">{taskStatus.id}</Descriptions.Item>
                <Descriptions.Item label="状态">
                  <Tag color={
                    taskStatus.status === 'RUNNING' ? 'processing' :
                    taskStatus.status === 'COMPLETED' ? 'success' :
                    taskStatus.status === 'FAILED' ? 'error' : 'default'
                  }>
                    {taskStatus.status === 'PENDING' ? '等待中' :
                     taskStatus.status === 'RUNNING' ? '运行中' :
                     taskStatus.status === 'COMPLETED' ? '已完成' :
                     taskStatus.status === 'FAILED' ? '失败' : '未知'}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="进度">
                  <Progress 
                    percent={taskStatus.progress} 
                    size="small" 
                    status={taskStatus.status === 'FAILED' ? 'exception' : 'active'} 
                  />
                </Descriptions.Item>
              </Descriptions>
            </Card>
          </Col>
        </Row>
      )}

      {/* 统计卡片 */}
      <Row gutter={[16, 16]}>
        <Col span={6}>
          <Card>
            <Statistic
              title="总使用次数"
              value={data?.totalUsage || 0}
              prefix={<BarChartOutlined />}
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="分析接口数"
              value={usageRanking.length}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="优化建议数"
              value={optimizationSuggestions.length}
              prefix={<WarningOutlined />}
              valueStyle={{ color: '#faad14' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Top5占比"
              value={data?.top5UsagePercentage ? data.top5UsagePercentage.toFixed(1) : '0.0'}
              suffix="%"
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
      </Row>

      <Divider />

      {/* 接口使用率排行榜 */}
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Card title="接口使用率排行榜" bordered={false}>
            <BarChart
              data={usageRanking}
              title="接口使用次数分布"
              xField="interfaceName"
              yField="totalCount"
              loading={loading}
              error={error}
            />
          </Card>
        </Col>
      </Row>

      <Divider />

      {/* 优化建议列表 */}
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Card 
            title={
              <span>
                <Badge count={optimizationSuggestions.length} style={{ backgroundColor: '#faad14' }} />
                &nbsp;建议加强优化列表（Top 5）
              </span>
            } 
            bordered={false}
          >
            {optimizationSuggestions.length > 0 ? (
              <Table
                columns={optimizationColumns}
                dataSource={optimizationSuggestions.slice(0, 5)}
                rowKey="interfaceId"
                pagination={false}
                loading={loading}
              />
            ) : (
              <div style={{ textAlign: 'center', padding: 20 }}>
                <Text>暂无优化建议</Text>
              </div>
            )}
          </Card>
        </Col>
      </Row>

      <Divider />

      {/* 完整排行榜 */}
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Card title="完整使用率排行榜" bordered={false}>
            <Table
              columns={usageRankingColumns}
              dataSource={usageRanking}
              rowKey="interfaceId"
              pagination={{ pageSize: 10 }}
              loading={loading}
            />
          </Card>
        </Col>
      </Row>
    </Content>
  );
};

export default OfflineAnalysis;