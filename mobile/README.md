# CloudDisk Pro Mobile

UniApp + Vue3 + Pinia + uView Plus 移动端，与 PC 端共用 Spring Boot API。

## 开发

```bash
cd mobile
npm install
npm run dev:h5
```

H5 开发地址：http://localhost:5174

API 通过 Vite 代理转发到 `http://127.0.0.1:8088`（与 PC 端一致）。

## 构建

```bash
npm run build:h5
```

产物目录：`mobile/dist/build/h5/`，部署到 Nginx `/www/clouddisk/mobile`。

PC 端构建产物部署到 `/www/clouddisk/pc`（`frontend/dist`）。

## 已实现（对照设计文档）

| 阶段 | 功能 |
|------|------|
| 一 | 登录、文件列表、上传、下载 |
| 二 | 回收站、分享查看、我的分享列表 |
| 三 | 团队空间（PC 端） |
| 四 | 分片/秒传（PC 端，移动端当前为简单上传） |
| 五 | APK 打包（manifest 已预留 Android 权限，HBuilderX 发行） |

## 页面结构

- 顶部导航 + 文件列表 + 底部 Tab（云盘 / 分享 / 回收站 / 我的）
- 图片预览、视频播放
- 公开分享页 `/pages/share/view`

## 鉴权

与 PC 一致：`Bearer` Token，本地存储 key 为 `cd_token`（H5 使用 `uni.setStorageSync`）。
