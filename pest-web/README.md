# pest-web 虫害治理 App PWA 员工端

网页版员工端，替代原生安卓 App。Vue3 + Vite + TypeScript，PWA（Service Worker + manifest）承载离线作业能力。

## 技术要点

- **离线存储**：结构化数据走 IndexedDB，照片原图走 OPFS，账号隔离（对应后端架构 OFF02）。
- **同步触发**：页面可见且联网时触发，不依赖后台常驻（规避浏览器切后台挂起，架构风险 R12）。
- **发布**：HTTPS 部署，Service Worker 版本管理，旧缓存作废、新资源上线即刷新。

## 目录

```
src/
  api/            axios 请求层（/api/v1 代理到后端 8080）
  services/       offlineStore.ts 离线存储封装（IndexedDB + OPFS）
  stores/         Pinia 状态
  router/         路由
  views/          LoginView / DashboardView / StoreListView
```

## 运行

```bash
npm install
npm run dev        # 本地开发，/api 代理到 localhost:8080
npm run build      # 生产构建（含 PWA 生成）
npm run preview    # 预览构建产物
```

## 说明

- 骨架阶段：登录、工作台、门店列表最小链路已就位，其余 20 页按 WP1–WP4 逐步接入。
- PWA 图标 pwa-192.png / pwa-512.png 需后续用 VI 品牌 Logo 生成后放入 public/。
- 环境要求：HTTPS（PWA 硬性要求，备案后部署）。
