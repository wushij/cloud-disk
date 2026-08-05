# CloudDisk Pro

企业级智能云盘系统，前后端分离架构。支持大文件分片上传、秒传、分享、在线预览/编辑、团队空间与企业级扩展组件。

| 服务 | 地址 |
|------|------|
| 前端 PC | http://localhost:5173 |
| 前端 Mobile H5 | http://localhost:5174 |
| 后端 API | http://127.0.0.1:8055 |
| 接口文档 | http://127.0.0.1:8055/doc.html |
| 健康检查 | http://127.0.0.1:8055/actuator/health |
| 监控面板 | http://127.0.0.1:8090 |
| 默认账号 | `admin` / `admin123` |

---

## 目录

- [技术栈](#技术栈)
- [功能概览](#功能概览)
- [架构](#架构)
- [环境要求](#环境要求)
- [环境配置](#环境配置)
- [快速开始（本地 Dev）](#快速开始本地-dev)
- [VS Code / Cursor 启动](#vs-code--cursor-启动)
- [Docker 依赖栈](#docker-依赖栈)
- [常用配置](#常用配置)
- [前端路由](#前端路由)
- [API 一览](#api-一览)
- [移动端（UniApp H5）](#移动端uniapp-h5)
- [测试与 CI](#测试与-ci)
- [目录结构](#目录结构)

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 PC | Vue 3 · TypeScript · Vite · Pinia · Element Plus · pdf.js · video.js · WebSocket |
| 前端 Mobile | UniApp · Vue 3 · Pinia · SCSS（H5 / 可打包 APK） |
| 后端 | Spring Boot 3.2.5 · Sa-Token 1.38 · MyBatis Plus 3.5.6 · MySQL 8 · Knife4j 4.5 |
| 缓存 | Redis（Sa-Token 独立库 + 业务缓存；`local,memory` 可降级内存） |
| 存储 | MinIO 8.5 / 本地磁盘 |
| 消息 | RabbitMQ（可选，异步转码） |
| 搜索 | MySQL LIKE / ElasticSearch 8.12 + IK + 拼音（可选） |
| 监控 | Spring Boot Admin 3.2 · SkyWalking · ELK（可选） |
| 国密安全 | HMAC-SM3 数字签名 · SM4-CBC 双向加解密 · 会话临时密钥协商 · 一次性认证标签（防重放/防篡改） |

---

## 功能概览

| 模块 | 能力 |
|------|------|
| 用户中心 | 注册（管理员审批）/ 登录、图形验证码、头像、个人信息；RBAC（`USER` / `ADMIN`） |
| 文件 | 上传/下载/重命名/移动/复制；MD5 秒传、分片、断点续传；最大 20GB |
| 文件夹 | 多级目录、树形导航、面包屑；文件夹级联回收站 |
| 分享 | 文件/文件夹分享、提取码、过期时间、下载计数去重；分享页预览（pdf.js / video.js / OnlyOffice 只读） |
| 预览 | 图片/PDF/视频/文本；FFmpeg 转码 H.264 + 封面；OnlyOffice 在线编辑 |
| 搜索 | MySQL 模糊搜索；ES 全文 + 拼音（profile `es`） |
| 团队空间 | 创建团队、成员邀请/接受/移除、团队头像、团队文件共享；成员可在「我的云盘」看到团队根目录 |
| 传输列表 | PC 右下角浮层 / Mobile 独立页；上传/下载队列、进度、暂停/恢复、清除已完成 |
| 通知 | 站内通知、未读角标、团队邀请接受/拒绝、注册审批通知、通知删除/清空（WebSocket 推送） |
| 安全 | BCrypt、登录失败锁定、图形验证码、API/IP 限流、Sentinel QPS、分享防暴力破解；ClamAV（可选）；**HMAC-SM3 签名 + SM4-CBC 加解密 + 时间戳/Nonce 防重放（热更新开关）** |
| CDN | MinIO 预签名直链 + CDN 域名替换，预览/下载优先走直链 |
| 管理后台 | 仪表盘、用户管理（注册审批/角色修改/密码重置/状态/配额）、存储统计、ES 索引重建、审计日志、**API 安全开关热更新**（`/admin`） |
| 企业扩展 | FFmpeg · RabbitMQ · XXL-JOB · Sentinel · OnlyOffice · LDAP/SSO · 监控/ELK |

> 国密加密链路开关、密钥协商与常见问题排查见 [docs/国密加密链路问题分析与修复记录.md](docs/国密加密链路问题分析与修复记录.md) — **HMAC-SM3 签名 / SM4-CBC 双向加解密与会话密钥协商**。

---

## 架构

```mermaid
flowchart LR
  subgraph client [客户端]
    Web[Vue 3 PC 前端]
    Mobile[UniApp Mobile H5]
  end
  subgraph gateway [接入层]
    Nginx[Nginx UA 分流]
  end
  subgraph app [应用层]
    API[Spring Boot API]
    WS[WebSocket 上传进度]
    Sec[ApiSecurityFilter<br/>HMAC-SM3 + SM4-CBC]
  end
  subgraph data [数据层]
    MySQL[(MySQL)]
    Redis[(Redis)]
    MinIO[(MinIO / 本地存储)]
    ES[(ElasticSearch 可选)]
  end
  subgraph async [异步 / 扩展]
    MQ[RabbitMQ]
    Job[XXL-JOB]
    FFmpeg[FFmpeg 转码]
    Admin[Spring Boot Admin]
  end
  Web --> Nginx --> API
  Mobile --> Nginx --> API
  Web --> WS
  Mobile --> WS
  API --> Sec
  Sec --> MySQL
  Sec --> Redis
  Sec --> MinIO
  Sec --> ES
  API --> MQ --> FFmpeg
  Job --> API
  API --> Admin
```

---

## 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+ |
| MySQL | 8.x |
| MinIO | 最新稳定版（或使用 Docker Compose） |

**可选：** Redis · RabbitMQ · ElasticSearch · FFmpeg · OnlyOffice · ClamAV

---

## 环境配置

数据库 **`cloud_disk`**，默认账号/密码 **`root` / `root`**。完整建表脚本见 [`sql/init.sql`](sql/init.sql)。

| Profile | 场景 | MySQL | Redis | 说明 |
|---------|------|-------|-------|------|
| `local` | 本地开发 | 3306 | 6379（无密码） | MinIO + Redis（Sa-Token 独立 db1） |
| `local,memory` | 无 Redis 降级 | 3306 | — | 纯内存缓存与 Token |
| `docker` | Compose 依赖 | **3307** | 6379（无密码） | 连接 Docker 映射端口，全组件启用 |
| `prod` | 服务器（**默认**） | 3306 | 6379（密码 `root`） | 后端端口 **8055**，Nginx 反代 |

配置文件：

| 文件 | 说明 |
|------|------|
| [`application.yml`](backend/src/main/resources/application.yml) | 公共基础配置（含 `api-security` 国密开关） |
| [`application-local.yml`](backend/src/main/resources/application-local.yml) | 本地开发 |
| [`application-docker.yml`](backend/src/main/resources/application-docker.yml) | Docker 依赖栈 |
| [`application-prod.yml`](backend/src/main/resources/application-prod.yml) | 生产环境 |
| [`application-monitoring.yml`](backend/src/main/resources/application-monitoring.yml) | Spring Boot Admin 客户端 |

**可选 profile**（叠加在 local/prod/docker 上）：

`redis` · `mq` · `es` · `xxl` · `onlyoffice` · `monitoring` · `elk` · `ldap` · `sso` · `clamav` · `memory`

生产环境变量：

| 变量 | 说明 |
|------|------|
| `SERVER_PORT` | 后端监听端口（默认 **8055**，Nginx `proxy_pass` 须一致） |
| `CLOUDDISK_DB_HOST` / `CLOUDDISK_DB_PORT` | MySQL 地址 |
| `CLOUDDISK_REDIS_HOST` / `CLOUDDISK_REDIS_PORT` / `CLOUDDISK_REDIS_PASSWORD` | Redis |
| `CLOUDDISK_MINIO_ENDPOINT` / `CLOUDDISK_MINIO_ACCESS_KEY` / `CLOUDDISK_MINIO_SECRET_KEY` / `CLOUDDISK_MINIO_BUCKET` | MinIO |
| `CLOUDDISK_CORS_ORIGIN` | 前端域名（CORS） |
| `CLOUDDISK_STORAGE` | 本地存储根目录 |
| `CLOUDDISK_RABBITMQ_HOST` / `CLOUDDISK_RABBITMQ_PORT` / `CLOUDDISK_RABBITMQ_USER` / `CLOUDDISK_RABBITMQ_PASS` | RabbitMQ |
| `CLOUDDISK_ES_URIS` | ElasticSearch 地址 |

---

## 快速开始（本地 Dev）

```bash
# 1. 启动依赖（二选一）
# 方式 A：Docker Compose（推荐，含 MySQL/Redis/MinIO/RabbitMQ/ES）
docker compose -f docker/docker-compose.yml up -d

# 方式 B：手动启动 MinIO
minio server ./data --console-address ":9001"
# 控制台 http://127.0.0.1:9001 创建桶 cloud-disk

# 2. 初始化数据库（Docker Compose 已自动执行，可跳过）
mysql -uroot -proot < sql/init.sql

# 3. 后端
cd backend
# Docker Compose 依赖用 docker profile（MySQL 3307）
SPRING_PROFILES_ACTIVE=docker mvn spring-boot:run -DskipTests
# 或本地 MySQL 3306 用 local profile
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run -DskipTests

# 4. PC 前端
cd frontend
npm install && npm run dev

# 5. 移动端 H5（可选）
cd mobile
npm install && npm run dev:h5
```

浏览器访问 http://localhost:5173（PC）或 http://localhost:5174（Mobile H5），使用 `admin` / `admin123` 登录。

> 首次启动前端会自动调用 `POST /api/auth/session-sign-init` 协商本次会话的 SM3 签名密钥与 SM4 加密密钥（默认开启时间戳/Nonce 校验），协商结果存 Redis（TTL 120 分钟）。

---

## VS Code / Cursor 启动

`.vscode/launch.json` 已配置，按 **F5** 选择：

| 配置 | 说明 |
|------|------|
| **后端 dev** | `local` profile（MySQL 3306 + Redis） |
| **后端 dev + 监控** | `local,monitoring` profile（含 Spring Boot Admin 客户端） |
| **后端 docker dev** | `docker` profile（连接 Docker Compose 的 MySQL 3307） |
| **前端 dev** | Vite :5173，代理后端 :8055 |
| **移动端 dev** | UniApp H5 :5174，代理后端 :8055 |
| **全栈**（复合） | 后端 dev + 前端 dev + 移动端 dev 同时启动 |

---

## Docker 依赖栈

`docker/` 目录包含完整的容器化配置：

| 文件 | 说明 |
|------|------|
| [`docker/docker-compose.yml`](docker/docker-compose.yml) | MySQL/Redis/MinIO/RabbitMQ/ES + 可选全栈（backend/nginx/admin-server） |
| [`docker/.env`](docker/.env) | Docker 环境变量（镜像仓库、代理配置） |
| [`docker/Dockerfile.backend`](docker/Dockerfile.backend) | 后端 JRE 17 镜像 |
| [`docker/Dockerfile.admin-server`](docker/Dockerfile.admin-server) | 监控 Admin Server 镜像 |
| [`docker/nginx.conf`](docker/nginx.conf) | Nginx 限流 / UA 分流 / 反向代理 / gzip |
| [`docker/elasticsearch/Dockerfile`](docker/elasticsearch/Dockerfile) | ES 8.12.2 + IK + 拼音插件 |

### 仅依赖（本机跑前后端）

```bash
docker compose -f docker/docker-compose.yml up -d
# MySQL 映射宿主机 3307，首次启动自动执行 sql/init.sql
SPRING_PROFILES_ACTIVE=docker mvn spring-boot:run -DskipTests   # backend/
npm run dev                                                        # frontend/
npm run dev:h5                                                     # mobile/
```

### 全栈部署（Nginx + 后端 + Admin 监控）

```bash
# 先构建产物
cd frontend && npm run build
cd mobile && npm run build:h5
cd backend && mvn package -DskipTests
cd monitoring/admin-server && mvn package -DskipTests

# 启动全栈
docker compose --env-file docker/.env -f docker/docker-compose.yml --profile app up -d --build
```

| 服务 | 地址 |
|------|------|
| PC / 移动端（Nginx UA 分流） | http://localhost:8080 |
| Spring Boot Admin 监控 | http://localhost:8090 |

```bash
# 停止
docker compose -f docker/docker-compose.yml --profile app down
# 查看日志
docker compose -f docker/docker-compose.yml logs -f
```

---

## 常用配置

| 配置项 | 说明 |
|--------|------|
| `clouddisk.storage.type` | `minio` 或 `local` |
| `clouddisk.minio.bucket` | 存储桶（默认 `cloud-disk`） |
| `clouddisk.redis.enabled` | Redis 缓存 + Sa-Token 持久化 |
| `clouddisk.cdn.enabled` | CDN 直链加速 |
| `clouddisk.ffmpeg.enabled` | 视频转码与封面（H.264 / AAC / 720p） |
| `clouddisk.sentinel.enabled` | 上传 QPS 限流（默认开） |
| `clouddisk.onlyoffice.enabled` | Office 在线编辑 |
| `clouddisk.elasticsearch.enabled` | ES 全文搜索 |
| `clouddisk.virus-scan.enabled` | ClamAV 扫描 |
| `clouddisk.rate-limit.*` | API / 登录 / 注册 / 分享限流 |
| `clouddisk.ldap.enabled` | LDAP 统一认证 |
| `clouddisk.sso.enabled` | SSO 单点登录 |
| `clouddisk.schedule.recycle-retain-days` | 回收站自动清理天数（默认 30） |

### API 全链路国密加固（`clouddisk.api-security.*`）

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `timestamp-enabled` | `true` | 时间戳校验（前后 5 分钟窗口） |
| `nonce-enabled` | `true` | Nonce 随机数防重放（Redis 去重） |
| `sm3-sign-enabled` | `false` | HMAC-SM3 数字签名（与 `sm2-sign-enabled` OR 取值） |
| `sm4-encrypt-enabled` | `false` | SM4-CBC 请求/响应双向加解密 |
| `sm3-sign-key` | `""` | 静态备用签名密钥（留空则使用会话随机密钥） |
| `sm4-secret-key` | `""` | 静态备用 SM4 密钥（16 字节或 32 位 Hex） |
| `session-sign-ttl-minutes` | `120` | 会话密钥 Redis TTL（分钟） |

> 建议生产开启 `sm3-sign-enabled` 与 `sm4-encrypt-enabled`；`/admin` →「API 全链路安全与国密加固防护控制」面板可热更新开关。

Profile 组合示例：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local,memory      # 无 Redis 时降级内存
mvn spring-boot:run -Dspring-boot.run.profiles=local,monitoring   # + Spring Boot Admin
mvn spring-boot:run -Dspring-boot.run.profiles=docker             # Docker Compose 全组件
mvn spring-boot:run -Dspring-boot.run.profiles=prod,monitoring    # 生产 + 监控
```

---

## 前端路由

| 路径 | 页面 | 权限 |
|------|------|------|
| `/login` | 登录 / 注册 | 公开 |
| `/disk` | 我的网盘 | 登录 |
| `/shares` | 我的分享 | 登录 |
| `/teams` | 团队空间 | 登录 |
| `/recycle` | 回收站 | 登录 |
| `/profile` | 个人中心 | 登录 |
| `/office/:id` | OnlyOffice 编辑 | 登录 |
| `/admin` | 管理后台（仪表盘/审计日志/安全开关） | ADMIN |
| `/admin/users` | 用户管理（注册审批/角色/密码/配额） | ADMIN |
| `/share/:code` | 分享访问页 | 公开 |

---

## API 一览

| 模块 | 路径 | 说明 |
|------|------|------|
| 认证 | `/api/auth/*` | 登录、注册（审批制）、验证码、LDAP/SSO、**会话密钥协商**（`session-sign-init`）、头像上传/查看、个人信息、退出 |
| 文件夹 | `/api/folders/*` | 树形目录、创建、重命名、移动、删除、面包屑 |
| 文件 | `/api/files/*` | 列表、上传、下载、预览、直链、搜索 |
| 上传 | `/api/upload/*` | MD5 校验、分片 init/chunk/merge、断点续传 |
| 分享 | `/api/share/*`、`/share/{code}/*` | 创建、取消、访问、预览、下载（IP 去重计数） |
| 回收站 | `/api/recycle/*` | 列表、恢复、级联删除/恢复 |
| 团队 | `/api/teams/*` | 团队 CRUD、成员、团队文件、邀请、团队头像 |
| 团队邀请 | `/api/team-invitations/*` | 待处理邀请、接受/拒绝 |
| 通知 | `/api/notifications/*` | 列表、未读数、标记已读/全部已读、删除、清空 |
| 存储 | `/api/storage/*` | 存储信息、用量、缓存统计 |
| 管理 | `/api/admin/*` | 仪表盘、用户列表、注册审批、角色修改、密码重置、状态/配额、存储统计、ES 重建、审计日志、**安全开关**（`security/config`） |
| OnlyOffice | `/api/files/{id}/onlyoffice`、`/api/onlyoffice/*` | 编辑配置与回调 |
| WebSocket | `/ws/upload?token=` | 上传进度推送 |

> `/api/admin/security/config` 在 `ApiSecurityFilter` 中豁免签名与加解密，避免「既改安全配置又要求安全配置通过」的死锁。

完整接口参数见 Knife4j：http://127.0.0.1:8055/doc.html

---

## 移动端（UniApp H5）

与 PC 共用同一套 Spring Boot API，独立工程位于 [`mobile/`](mobile/)。开发：`cd mobile && npm run dev:h5` → http://localhost:5174

### 底部导航

| Tab | 页面 | 说明 |
|-----|------|------|
| 云盘 | `/pages/disk/index` | 文件列表/宫格、搜索、面包屑、上传/新建文件夹；头部入口进入传输列表 |
| 分享 | `/pages/shares/index` | 我的分享链接管理 |
| 团队 | `/pages/teams/index` | 团队列表、创建团队、进入团队文件 |
| 我的 | `/pages/profile/index` | 头像/容量、传输列表、通知、分享/回收站/团队入口、退出登录 |

### 其他页面

| 路径 | 说明 |
|------|------|
| `/pages/login/index` | 登录 / 注册（与 PC 账号互通，注册需管理员审批） |
| `/pages/recycle/index` | 回收站（从「我的」入口进入） |
| `/pages/teams/files` | 团队文件浏览、上传/下载/删除 |
| `/pages/teams/members` | 成员管理、邀请成员 |
| `/pages/notifications/index` | 消息通知（团队邀请/注册审批/分享通知；删除/清空） |
| `/pages/transfer/index` | 传输列表（上传/下载队列、进度、暂停/恢复） |
| `/pages/share/view` | 公开分享访问（提取码） |
| `/pages/preview/image` · `/pages/preview/video` · `/pages/preview/text` | 图片/视频/文本预览 |
| `/pages/admin/users` | 用户管理（ADMIN 权限，注册审批/角色/状态） |

### 传输列表

| 端 | 入口 | 能力 |
|----|------|------|
| PC | 网盘页右下角 `TransferPanel` 浮层 | 上传/下载队列、进度与速率、暂停/恢复上传、清除已完成 |
| Mobile | 云盘头部图标 → `/pages/transfer/index`；「我的 → 传输列表」 | 同上；H5 流式下载；进行中任务角标实时更新 |

任务状态由 Pinia [`mobile/src/stores/transfer.ts`](mobile/src/stores/transfer.ts) / [`frontend/src/stores/transfer.ts`](frontend/src/stores/transfer.ts) 统一管理。

### 团队空间（Mobile）

- 创建团队、浏览/上传/下载团队文件（成员共享访问，后端 `getOwnedOrShared` 鉴权）
- 邀请成员 → 对方在通知中心接受后成为成员
- 成员在「我的云盘」根目录可见团队文件夹，进入后与普通目录体验一致

### 界面风格

- 顶部头部卡片与底部 Tab 采用统一 **淡粉描边** 设计（`#fffbfb` 底 + `#f0d4d4` 边框，见 [`mobile/src/styles/theme.scss`](mobile/src/styles/theme.scss) 中 `--cd-accent-*` 变量）
- 自定义弹窗：`MobilePromptDialog`（输入）、`MobileConfirmDialog`（确认）
- 更多移动端构建说明见 [`mobile/README.md`](mobile/README.md)

---

## 测试与 CI

**后端单元测试：**

```bash
cd backend && mvn test
```

覆盖：文件校验、上传/分享服务、登录防护、全局异常处理、`ApiSecurityFilter` 签名/SM4 验签等。

**前端单元测试：**

```bash
cd frontend && npm run test:unit
```

**前端构建：**

```bash
cd frontend && npm run build
```

**CI 流水线：** [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — push/PR 到 `main`/`master` 时自动执行：

- 后端：JDK 17（Temurin） + `mvn test package`
- 前端：Node 20 + `npm ci && npm run test:unit && npm run build`

---

## 目录结构

```
├── backend/                                  # Spring Boot 主服务
│   ├── src/main/java/com/clouddisk/
│   │   ├── controller/                       # Controller（Auth/File/Folder/Upload/Share/Recycle/Team/Notification/Storage/Admin/OnlyOffice/…）
│   │   ├── service/                          # 业务服务
│   │   ├── mapper/  entity/  dto/            # MyBatis Plus
│   │   ├── config/                           # CloudDiskProperties / SecurityConfig / WebConfig / Sentinel / XxlJob / SaToken / …
│   │   ├── security/                         # ApiSecurityFilter · SecurityConfigService · LoginProtectionService · MediaAccessTokenService · WebSocketTicketService · VirusScanService
│   │   ├── cache/  common/  util/            # 缓存 / 通用响应 / 工具
│   │   ├── media/  storage/  search/        # FFmpeg / MinIO+本地 / ES
│   │   ├── mq/  job/  onlyoffice/  websocket/# RabbitMQ / XXL-JOB / OnlyOffice / WS
│   │   ├── team/  migration/  auth/          # 团队 / 启动迁移 / 认证辅助
│   │   └── CloudDiskApplication.java
│   ├── src/main/resources/                   # 配置（application.yml / application-*.yml · logback-spring.xml · es/）
│   └── src/test/                             # 单元测试
├── frontend/                                 # Vue 3 PC 前端
│   ├── src/views/                            # Disk / Shares / TeamSpace / Recycle / Admin / UserManage / Profile / Login / SharePage / OfficeEditor
│   ├── src/components/                       # ConfirmDialog / FolderTypeIcon / TransferPanel / ShareDialog / PdfPreview / VideoPreview / TextPreview 等
│   ├── src/stores/                           # Pinia 状态（auth / file / notification / transfer / confirmDialog / theme）
│   ├── src/utils/                            # 工具（加密、签名、上传、下载、错误处理、文件预览/封面、WebSocket 等）
│   └── src/api/  src/router/  src/styles/    # http（axios 拦截器）/ 路由 / 全局样式
├── mobile/                                   # UniApp 移动端（H5 / APK）
│   ├── src/pages/                            # 登录、云盘、分享、团队、传输、通知、回收站、预览、用户管理等
│   ├── src/stores/                           # auth / file / transfer / notification
│   ├── src/components/                       # MobileHeader / TabBar / ConfirmDialog / EmptyState / 弹窗等
│   └── README.md                             # 移动端开发与构建说明
├── docker/                                   # Docker 容器化配置
│   ├── docker-compose.yml                    # 依赖栈编排（MySQL / Redis / MinIO / RabbitMQ / ES + 可选全栈 profile）
│   ├── .env                                  # Docker 环境变量（镜像仓库、代理）
│   ├── Dockerfile.backend                    # 后端 JRE 17 镜像
│   ├── Dockerfile.admin-server               # 监控 Admin Server 镜像
│   ├── nginx.conf                            # Nginx 限流 / UA 分流 / 反向代理 / gzip / 安全 header
│   └── elasticsearch/Dockerfile              # ES 8.12.2 + IK + 拼音插件
├── monitoring/admin-server/                  # Spring Boot Admin 监控服务端（:8090）
├── scripts/                                  # 构建辅助脚本（vite-lan-banner.mjs）
├── sql/
│   └── init.sql                              # 完整数据库脚本（唯一入口）
├── .github/workflows/ci.yml                  # GitHub Actions CI
├── .dockerignore                             # Docker 构建排除规则
└── .vscode/                                  # launch.json / settings.json
```
