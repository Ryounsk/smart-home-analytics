import React, { useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { 
  Layout, Card, Form, Input, Button, Select, 
  Switch, InputNumber, Typography, message, Row, Col
} from 'antd';
import { SaveOutlined, ReloadOutlined } from '@ant-design/icons';
import { RootState, AppDispatch } from '../../store';
import { saveSystemConfig } from '../../store/slices/systemSlice';

const { Title } = Typography;
const { Content } = Layout;
const { Option } = Select;

const SystemSettings: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>();
  const { config, loading, error } = useSelector((state: RootState) => state.system);
  
  const [form] = Form.useForm();
  const [initialValues, setInitialValues] = useState(config);

  // 表单提交
  const handleSubmit = () => {
    form.validateFields()
      .then(values => {
        dispatch(saveSystemConfig(values))
          .unwrap()
          .then(() => {
            message.success('配置保存成功');
            setInitialValues(values);
          })
          .catch((err) => {
            message.error(`保存失败: ${err}`);
          });
      })
      .catch(info => {
        message.error('表单验证失败');
      });
  };

  // 重置表单
  const handleReset = () => {
    form.setFieldsValue(initialValues);
  };

  return (
    <Content>
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Title level={2}>系统设置</Title>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col span={16}>
          <Card title="系统配置" bordered={false}>
            <Form
              form={form}
              layout="vertical"
              initialValues={initialValues}
              onFinish={handleSubmit}
            >
              <Form.Item
                name="refreshInterval"
                label="数据刷新间隔（秒）"
                rules={[
                  { required: true, message: '请输入刷新间隔' },
                  { type: 'number', min: 5, max: 300, message: '间隔范围5-300秒' }
                ]}
              >
                <InputNumber 
                  min={5} 
                  max={300} 
                  style={{ width: 120 }} 
                  formatter={value => `${value} 秒`}
                  parser={value => value?.replace(' 秒', '')}
                />
              </Form.Item>

              <Form.Item
                name="errorThreshold"
                label="熔断错误率阈值（%）"
                rules={[
                  { required: true, message: '请输入熔断阈值' },
                  { type: 'number', min: 1, max: 100, message: '阈值范围1-100%' }
                ]}
              >
                <InputNumber 
                  min={1} 
                  max={100} 
                  style={{ width: 120 }} 
                  formatter={value => `${value} %`}
                  parser={value => value?.replace(' %', '')}
                />
              </Form.Item>

              <Form.Item
                name="warningThreshold"
                label="警告错误率阈值（%）"
                rules={[
                  { required: true, message: '请输入警告阈值' },
                  { type: 'number', min: 1, max: 100, message: '阈值范围1-100%' }
                ]}
              >
                <InputNumber 
                  min={1} 
                  max={100} 
                  style={{ width: 120 }} 
                  formatter={value => `${value} %`}
                  parser={value => value?.replace(' %', '')}
                />
              </Form.Item>

              <Form.Item
                name="dataRetentionDays"
                label="数据保留天数"
                rules={[
                  { required: true, message: '请输入保留天数' },
                  { type: 'number', min: 1, max: 365, message: '保留范围1-365天' }
                ]}
              >
                <InputNumber 
                  min={1} 
                  max={365} 
                  style={{ width: 120 }} 
                  formatter={value => `${value} 天`}
                  parser={value => value?.replace(' 天', '')}
                />
              </Form.Item>

              <Form.Item
                name="notificationEnabled"
                label="启用通知"
                valuePropName="checked"
              >
                <Switch checkedChildren="启用" unCheckedChildren="禁用" />
              </Form.Item>

              <Form.Item>
                <Space>
                  <Button 
                    type="primary" 
                    htmlType="submit" 
                    icon={<SaveOutlined />}
                    loading={loading}
                  >
                    保存配置
                  </Button>
                  <Button 
                    type="default" 
                    icon={<ReloadOutlined />}
                    onClick={handleReset}
                  >
                    重置
                  </Button>
                </Space>
              </Form.Item>
            </Form>
          </Card>
        </Col>

        <Col span={8}>
          <Card title="系统信息" bordered={false}>
            <div style={{ lineHeight: 2 }}>
              <p><strong>系统版本：</strong> v1.0.0</p>
              <p><strong>前端框架：</strong> React 18</p>
              <p><strong>UI组件库：</strong> Ant Design 5.0</p>
              <p><strong>状态管理：</strong> Redux Toolkit</p>
              <p><strong>数据可视化：</strong> ECharts 5.4</p>
              <p><strong>构建工具：</strong> Vite</p>
              <p><strong>开发语言：</strong> TypeScript</p>
            </div>
          </Card>
        </Col>
      </Row>
    </Content>
  );
};

export default SystemSettings;