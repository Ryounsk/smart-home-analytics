import React, { useState, useEffect } from 'react';
import { Layout, Menu, Spin, Alert, message } from 'antd';
import { 
  DashboardOutlined, 
  LineChartOutlined, 
  BarChartOutlined, 
  SettingOutlined,
  LoadingOutlined
} from '@ant-design/icons';
import { Routes, Route, Link, useLocation } from 'react-router-dom';
import RealTimeMonitor from './pages/realtime/RealTimeMonitor';
import OfflineAnalysis from './pages/offline/OfflineAnalysis';
import SystemSettings from './pages/system/SystemSettings';
import './assets/styles/App.css';

const { Header, Content, Sider } = Layout;
const { SubMenu } = Menu;

const App: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const [loading, setLoading] = useState(true);
  const location = useLocation();

  useEffect(() => {
    // 模拟初始化加载
    const timer = setTimeout(() => {
      setLoading(false);
    }, 1500);
    
    return () => clearTimeout(timer);
  }, []);

  const handleMenuClick = (e: any) => {
    message.info(`切换到${e.item.props.title}`);
  };

  if (loading) {
    return (
      <div className="app-loading">
        <Spin 
          indicator={<LoadingOutlined style={{ fontSize: 48 }} spin />} 
          tip="系统初始化中..." 
        />
      </div>
    );
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider 
        collapsible 
        collapsed={collapsed} 
        onCollapse={setCollapsed}
        theme="dark"
      >
        <div className="logo">
          <h1>智能家居分析</h1>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          onClick={handleMenuClick}
        >
          <Menu.Item key="/realtime" icon={<DashboardOutlined />} title="实时监控">
            <Link to="/realtime">实时监控</Link>
          </Menu.Item>
          
          <SubMenu key="analysis" icon={<LineChartOutlined />} title="数据分析">
            <Menu.Item key="/offline" icon={<BarChartOutlined />} title="离线分析">
              <Link to="/offline">离线分析</Link>
            </Menu.Item>
          </SubMenu>
          
          <Menu.Item key="/system" icon={<SettingOutlined />} title="系统设置">
            <Link to="/system">系统设置</Link>
          </Menu.Item>
        </Menu>
      </Sider>
      
      <Layout className="site-layout">
        <Header className="site-layout-background" style={{ padding: '0 20px' }}>
          <h2 className="header-title">智能家居日志分析系统</h2>
        </Header>
        
        <Content style={{ margin: '24px 16px', padding: 24, background: '#fff', minHeight: 280 }}>
          <Routes>
            <Route path="/realtime" element={<RealTimeMonitor />} />
            <Route path="/offline" element={<OfflineAnalysis />} />
            <Route path="/system" element={<SystemSettings />} />
            <Route path="*" element={<RealTimeMonitor />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
};

export default App;