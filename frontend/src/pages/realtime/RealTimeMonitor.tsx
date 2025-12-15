import React, { useEffect, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { 
  Layout, Card, Statistic, Row, Col, Table, Tag, Button, 
  Typography, Divider, Progress, Badge, Space, Popconfirm, message
} from 'antd';
import { 
  ReloadOutlined, WarningOutlined, CheckCircleOutlined, 
  ExclamationCircleOutlined, ClockCircleOutlined
} from '@ant-design/icons';
import { RootState, AppDispatch } from '../../store';
import { 
  fetchRealTimeData, 
  resetCircuitBreakerStatus 
} from '../../store/slices/realtimeSlice';
import BarChart from '../../components/charts/BarChart';
import { formatDate } from '../../utils/dateUtils';
import { formatPercentage } from '../../utils/numberUtils';
import { InterfaceStats } from '../../types';

const { Title, Text } = Typography;
const { Content } = Layout;

const RealTimeMonitor: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>();
  const { 
    data, 
    errorRateRanking, 
    circuitBreakerList, 
    normalInterfaceList, 
    loading, 
    error,
    lastUpdated 
  } = useSelector((state: RootState) => state.realtime);
  
  const [refreshInterval, setRefreshInterval] = useState<NodeJS.Timeout | null>(null);

  // 表格列定义
  const circuitBreakerColumns = [
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
      title: '总请求数',
      dataIndex: 'totalCount',
      key: 'totalCount',
    },
    {
      title: '错误数',
      dataIndex: 'errorCount',
      key: 'errorCount',
      render: (text: number) => <Text type="danger">{text}</Text>,
    },
    {
      title: '错误率',
      dataIndex: 'errorRate',
      key: 'errorRate',
      render: (rate: number) => (
        <div>
          <Progress 
            percent={(rate * 100).toFixed(1)} 
            size="small" 
            status={rate >= 0.05 ? 'exception' : 'active'} 
          />
          <span style={{ marginLeft: 8 }}>{formatPercentage(rate)}</span>
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag color="error" icon={<ExclamationCircleOutlined />}>
          熔断中
        </Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record: InterfaceStats) => (
        <Popconfirm
          title="确定要重置熔断状态吗？"
          onConfirm={() => handleResetCircuitBreaker(record.interfaceId)}
          okText="确定"
          cancelText="取消"
        >
          <Button type="primary" size="small">
            重置熔断
          </Button>
        </Popconfirm>
      ),
    },
  ];

  const normalInterfaceColumns = [
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
      title: '总请求数',
      dataIndex: 'totalCount',
      key: 'totalCount',
    },
    {
      title: '错误数',
      dataIndex: 'errorCount',
      key: 'errorCount',
    },
    {
      title: '错误率',
      dataIndex: 'errorRate',
      key: 'errorRate',
      render: (rate: number) => (
        <div>
          <Progress 
            percent={(rate * 100).toFixed(1)} 
            size="small" 
            status={rate >= 0.02 ? 'warning' : 'success'} 
          />
          <span style={{ marginLeft: 8 }}>{formatPercentage(rate)}</span>
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag color={status === 'WARNING' ? 'warning' : 'success'}>
          {status === 'WARNING' ? '警告' : '正常'}
        </Tag>
      ),
    },
  ];

  // 处理刷新数据
  const handleRefresh = () => {
    dispatch(fetchRealTimeData());
  };

  // 处理重置熔断
  const handleResetCircuitBreaker = (interfaceId: string) => {
    dispatch(resetCircuitBreakerStatus(interfaceId))
      .unwrap()
      .then(() => {
        message.success('熔断状态已重置');
        handleRefresh();
      })
      .catch((err) => {
        message.error(`重置失败: ${err}`);
      });
  };

  // 自动刷新
  useEffect(() => {
    handleRefresh();
    
    // 设置自动刷新（30秒）
    const interval = setInterval(() => {
      handleRefresh();
    }, 30000);
    
    setRefreshInterval(interval);
    
    return () => {
      if (refreshInterval) {
        clearInterval(refreshInterval);
      }
    };
  }, [dispatch]);

  return (
    <Content>
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <Title level={2}>实时监控面板</Title>
            <Space>
              <Text>
                <ClockCircleOutlined /> 上次更新: {lastUpdated ? formatDate(lastUpdated) : '从未更新'}
              </Text>
              <Button 
                type="primary" 
                icon={<ReloadOutlined />} 
                onClick={handleRefresh}
                loading={loading}
              >
                刷新数据
              </Button>
            </Space>
          </div>
        </Col>
      </Row>

      {/* 统计卡片 */}
      <Row gutter={[16, 16]}>
        <Col span={6}>
          <Card>
            <Statistic
              title="总接口数"
              value={data?.totalInterfaces || 0}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="熔断接口数"
              value={circuitBreakerList.length}
              prefix={<WarningOutlined />}
              valueStyle={{ color: '#cf1322' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="总错误数"
              value={data?.totalErrors || 0}
              prefix={<ExclamationCircleOutlined />}
              valueStyle={{ color: '#faad14' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="平均错误率"
              value={data?.averageErrorRate ? (data.averageErrorRate * 100).toFixed(2) : '0.00'}
              suffix="%"
              valueStyle={{ 
                color: data?.averageErrorRate >= 0.05 ? '#cf1322' : 
                       data?.averageErrorRate >= 0.02 ? '#faad14' : '#52c41a' 
              }}
            />
          </Card>
        </Col>
      </Row>

      <Divider />

      {/* 错误率排行榜 */}
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Card title="接口错误率排行榜" bordered={false}>
            <BarChart
              data={errorRateRanking}
              title="接口错误率分布"
              xField="interfaceName"
              yField="errorRate"
              loading={loading}
              error={error}
            />
          </Card>
        </Col>
      </Row>

      <Divider />

      {/* 熔断接口列表 */}
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Card 
            title={
              <span>
                <Badge count={circuitBreakerList.length} style={{ backgroundColor: '#cf1322' }} />
                &nbsp;熔断接口列表
              </span>
            } 
            bordered={false}
          >
            {circuitBreakerList.length > 0 ? (
              <Table
                columns={circuitBreakerColumns}
                dataSource={circuitBreakerList}
                rowKey="interfaceId"
                pagination={{ pageSize: 5 }}
                loading={loading}
              />
            ) : (
              <div style={{ textAlign: 'center', padding: 20 }}>
                <CheckCircleOutlined style={{ fontSize: 24, color: '#52c41a' }} />
                <p style={{ marginTop: 8 }}>暂无熔断接口</p>
              </div>
            )}
          </Card>
        </Col>
      </Row>

      <Divider />

      {/* 正常接口列表 */}
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Card title="正常接口列表" bordered={false}>
            <Table
              columns={normalInterfaceColumns}
              dataSource={normalInterfaceList}
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

export default RealTimeMonitor;