# CloudDisk Pro Mobile

UniApp + Vue3 + Pinia + uView Plus 移动端，与 PC 端共用 Spring Boot API。

## 开发

```bash
cd mobile
npm install
npm run dev:h5
```

H5 开发地址：http://localhost:5174

API 通过 Vite 代理转发到 `http://127.0.0.1:8055`（与 PC 端一致）。

> 手机通过局域网 IP 访问 H5 时，上传/下载依赖的任务 ID 已兼容非 HTTPS 环境（不依赖 `crypto.randomUUID`）。

## 构建

```bash
npm run build:h5
```

产物目录：`mobile/dist/build/h5/`，部署到 Nginx `/www/clouddisk/mobile`。

PC 端构建产物部署到 `/www/clouddisk/pc`（`frontend/dist`）。

## 功能一览

| 模块 | 说明 |
|------|------|
| 登录 | 账号密码 + 图形验证码（与 PC 互通，无独立注册页） |
| 云盘 | 列表/宫格、搜索、面包屑、上传/新建文件夹、文件操作菜单 |
| 传输列表 | 上传/下载队列、进度与速率、暂停/恢复上传、清除已完成；云盘头部角标显示进行中任务数 |
| 分享 | 我的分享管理；公开分享页预览/下载 |
| 团队 | 团队列表、创建、团队文件、成员管理、邀请（通知中心接受） |
| 通知 | 未读角标（「我的」Tab）、团队邀请处理 |
| 我的 | 头像/容量、传输列表、分享/回收站/团队入口、退出登录 |
| 预览 | 图片、视频内页预览 |

## 页面与导航

底部 Tab：**云盘 · 分享 · 团队 · 我的**（回收站入口在「我的」内）。

| 路径 | 页面 |
|------|------|
| `pages/login/index` | 登录 |
| `pages/disk/index` | 我的云盘 |
| `pages/shares/index` | 我的分享 |
| `pages/teams/index` | 团队空间 |
| `pages/teams/files` | 团队文件 |
| `pages/transfer/index` | 传输列表 |
| `pages/notifications/index` | 消息通知 |
| `pages/recycle/index` | 回收站 |
| `pages/profile/index` | 个人中心 |
| `pages/share/view` | 公开分享 |
| `pages/preview/image` · `pages/preview/video` | 预览 |

## 主要 Store

| Store | 文件 | 职责 |
|-------|------|------|
| `auth` | `stores/auth.ts` | 登录态、用户信息、头像 |
| `file` | `stores/file.ts` | 云盘目录、列表、面包屑 |
| `transfer` | `stores/transfer.ts` | 上传/下载任务队列 |
| `notification` | `stores/notification.ts` | 通知列表、未读数、WebSocket |

## 界面

- 顶部 `MobileHeader` 与底部 `MobileTabBar` 使用统一淡粉描边卡片（主题变量 `--cd-accent-surface` / `--cd-accent-border`）
- 通用组件：`EmptyState`、`MobilePromptDialog`、`MobileConfirmDialog`、`FileListItem` 等

## 鉴权

与 PC 一致：`Bearer` Token，本地存储 key 为 `cd_token`（H5 使用 `uni.setStorageSync`）。
