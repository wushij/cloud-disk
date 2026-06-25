# CloudDisk Pro — Docker Hub 推送 / 拉取 / 部署指南

Docker Hub 仓库：[wu17/disk](https://hub.docker.com/r/wu17/disk)

| 类型 | 镜像 |
|------|------|
| **你的镜像（3 个）** | `wu17/disk:backend-latest`、`wu17/disk:elasticsearch-8.12.2-ik-pinyin`、`wu17/disk:admin-server-latest` |
| **官方镜像（5 个，compose 自动拉）** | `mysql:8.0`、`redis:7-alpine`、`minio/minio:latest`、`rabbitmq:3-management-alpine`、`nginx:alpine` |

默认账号：`admin` / `admin123`

---

## 一、推送到 Docker Hub（维护者）

### 1. 环境要求

| 工具 | 版本 |
|------|------|
| Docker Desktop | 最新稳定版 |
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+（构建前端静态页时需要） |

### 2. 登录 Docker Hub

```bash
docker login
```

用户名：`wu17`

### 3. 构建并推送 backend

```bash
# 项目根目录
cd backend
mvn -B package -DskipTests

cd ..
docker compose -f docker/docker-compose.yml build backend
docker push wu17/disk:backend-latest
```

### 4. 构建并推送 admin-server

```bash
cd monitoring/admin-server
mvn -B package -DskipTests

cd ../..
docker compose -f docker/docker-compose.yml build admin-server
docker push wu17/disk:admin-server-latest
```

### 5. 构建并推送 elasticsearch（IK + 拼音）

```bash
docker build -t wu17/disk:elasticsearch-8.12.2-ik-pinyin \
  -f docker/elasticsearch/Dockerfile docker/elasticsearch

docker push wu17/disk:elasticsearch-8.12.2-ik-pinyin
```

> ES 镜像较大（约 2GB），首次 build 和 push 耗时较长。

### 6. 一键推送三个镜像

```bash
# 在项目根目录执行（需先完成上述 mvn package / docker build）
docker push wu17/disk:backend-latest
docker push wu17/disk:admin-server-latest
docker push wu17/disk:elasticsearch-8.12.2-ik-pinyin
```

### 7. 验证推送结果

浏览器打开：https://hub.docker.com/r/wu17/disk/tags

或本地试拉：

```bash
docker pull wu17/disk:backend-latest
```

---

## 二、从 Docker Hub 拉取（部署者）

### 方式 A：手动拉取你的 3 个镜像

```bash
docker pull wu17/disk:backend-latest
docker pull wu17/disk:elasticsearch-8.12.2-ik-pinyin
docker pull wu17/disk:admin-server-latest
```

### 方式 B：compose 启动时自动拉（推荐）

执行 `docker compose up` 时，会根据 `docker-compose.yml` 中的 `image:` 自动拉取缺失镜像，**无需提前手动 pull**。

官方 5 个镜像（mysql / redis / minio / rabbitmq / nginx）同样由 compose 自动拉取。

---

## 三、部署构建步骤（全栈）

### 部署目录结构

部署者需要以下文件（不含 Java / 前端源码）：

```
clouddisk/
├── docker/
│   ├── docker-compose.yml
│   └── nginx.conf
├── sql/
│   └── init.sql
├── frontend/dist/              # PC 前端构建产物
└── mobile/dist/build/h5/       # 移动端 H5 构建产物
```

> `frontend/dist` 与 `mobile/dist` 由维护者本地 build 后一并提供；不在 Docker 镜像内。

### 步骤 1：构建前端静态页（维护者执行一次）

```bash
# PC 前端
cd frontend
npm ci
npm run build

# 移动端 H5
cd ../mobile
npm ci
npm run build:h5
```

### 步骤 2：拉取镜像（部署者，可选）

```bash
docker pull wu17/disk:backend-latest
docker pull wu17/disk:elasticsearch-8.12.2-ik-pinyin
docker pull wu17/disk:admin-server-latest
```

### 步骤 3：启动全栈

```bash
# 在项目根目录
docker compose -f docker/docker-compose.yml --profile app up -d --no-build
```

| 参数 | 说明 |
|------|------|
| `--profile app` | 启动 nginx + backend + admin-server（全栈） |
| `--no-build` | 只使用 Hub 已拉取的镜像，不在本地编译 |

### 步骤 4：访问

| 地址 | 说明 |
|------|------|
| http://localhost:8080 | 云盘（PC / 手机 H5 同端口，Nginx 按 UA 分流） |
| http://localhost:8055 | 后端 API |
| http://localhost:8055/doc.html | 接口文档 |
| http://localhost:8090 | Spring Boot Admin 监控 |
| 账号 | `admin` / `admin123` |

### 步骤 5：常用运维命令

```bash
# 查看容器状态
docker compose -f docker/docker-compose.yml ps

# 查看日志
docker compose -f docker/docker-compose.yml logs -f

# 查看单个服务日志
docker compose -f docker/docker-compose.yml logs -f backend

# 停止全栈
docker compose -f docker/docker-compose.yml --profile app down

# 停止并删除数据卷（清空数据库等，慎用）
docker compose -f docker/docker-compose.yml --profile app down -v
```

---

## 四、仅启动依赖栈（本机跑前后端源码）

若部署者要在本机用源码调试，只启动 MySQL / Redis / MinIO 等基础设施：

```bash
docker compose -f docker/docker-compose.yml up -d

# 后端（连接 Docker MySQL 3307）
cd backend
# Windows PowerShell:
$env:SPRING_PROFILES_ACTIVE="docker"; mvn spring-boot:run -DskipTests
# Linux / macOS:
SPRING_PROFILES_ACTIVE=docker mvn spring-boot:run -DskipTests

# PC 前端
cd frontend && npm run dev

# 移动端 H5（可选）
cd mobile && npm run dev:h5
```

| 地址 | 说明 |
|------|------|
| http://localhost:5173 | PC 前端 |
| http://localhost:5174 | 移动端 H5 |
| http://localhost:8055 | 后端 API |

---

## 五、端口一览

| 服务 | 宿主机端口 |
|------|-----------|
| Nginx（PC / H5） | 8080 |
| Backend API | 8055 |
| Admin 监控 | 8090 |
| MySQL | 3307 |
| Redis | 6379 |
| MinIO API / 控制台 | 9000 / 9001 |
| RabbitMQ / 管理台 | 5672 / 15672 |
| Elasticsearch | 9200 |

---

## 六、常见问题

### Q：`admin-server-latest: not found`

Hub 上尚未推送该 tag。维护者执行：

```bash
cd monitoring/admin-server && mvn package -DskipTests
docker compose -f docker/docker-compose.yml build admin-server
docker push wu17/disk:admin-server-latest
```

### Q：9000 端口被占用

本机已有 MinIO 或其他程序占用 9000。关闭占用进程，或修改 `docker-compose.yml` 中 minio 的端口映射。

### Q：8080 打开是空白页

`frontend/dist` 或 `mobile/dist/build/h5` 目录不存在或为空。需先执行第三节「步骤 1」构建前端。

### Q：后端启动报数据库表不存在

首次启动需挂载 `sql/init.sql`（compose 已配置）。若 MySQL 数据卷已存在但为空库，删除卷后重启：

```bash
docker compose -f docker/docker-compose.yml --profile app down -v
docker compose -f docker/docker-compose.yml --profile app up -d --no-build
```

---

## 七、快速命令速查

```bash
# 【维护者】构建 + 推送全部
cd backend && mvn -B package -DskipTests && cd ..
cd monitoring/admin-server && mvn -B package -DskipTests && cd ../..
docker compose -f docker/docker-compose.yml build backend admin-server
docker build -t wu17/disk:elasticsearch-8.12.2-ik-pinyin -f docker/elasticsearch/Dockerfile docker/elasticsearch
docker push wu17/disk:backend-latest
docker push wu17/disk:admin-server-latest
docker push wu17/disk:elasticsearch-8.12.2-ik-pinyin

# 【部署者】拉取 + 启动
docker pull wu17/disk:backend-latest
docker pull wu17/disk:elasticsearch-8.12.2-ik-pinyin
docker pull wu17/disk:admin-server-latest
docker compose -f docker/docker-compose.yml --profile app up -d --no-build
```
