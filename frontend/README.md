# 智能家居日志分析系统 - 前端项目

## 项目概述
智能家居日志分析系统前端，用于实时监控设备接口状态、展示错误率排行榜、熔断接口列表、正常接口列表，以及离线分析的接口使用率排行榜和优化建议。

## 技术栈
- **前端框架**: React 18
- **UI组件库**: Ant Design 5.0
- **状态管理**: Redux Toolkit
- **数据可视化**: ECharts 5.4
- **HTTP客户端**: Axios
- **构建工具**: Vite
- **语言**: TypeScript

## 项目结构
```
smart-home-analytics-frontend/
├── public/
│   ├── favicon.ico
│   └── index.html
├── src/
│   ├── assets/            # 静态资源
│   │   ├── images/
│   │   └── styles/
│   ├── components/        # 通用组件
│   │   ├── common/        # 公共组件
│   │   ├── charts/        # 图表组件
│   │   └── layout/        # 布局组件
│   ├── pages/             # 页面组件
│   │   ├── realtime/      # 实时监控页面
│   │   ├── offline/       # 离线分析页面
│   │   └── system/        # 系统管理页面
│   ├── services/          # API服务
│   │   ├── api.ts         # API基础配置
│   │   ├── realtime.ts    # 实时接口服务
│   │   └── offline.ts     # 离线接口服务
│   ├── store/             # Redux状态管理
│   │   ├── index.ts
│   │   ├── slices/
│   │   └── selectors/
│   ├── types/             # TypeScript类型定义
│   ├── utils/             # 工具函数
│   ├── App.tsx            # 应用入口
│   ├── main.tsx           # 主入口
│   └── vite-env.d.ts      # Vite环境声明
├── .env.development       # 开发环境配置
├── .env.production        # 生产环境配置
├── .gitignore             # Git忽略文件
├── index.html             # HTML入口
├── package.json           # 项目依赖
├── tsconfig.json          # TypeScript配置
├── vite.config.ts         # Vite配置
└── README.md              # 项目说明