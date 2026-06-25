# CloudDisk Pro — 宝塔 Docker 部署指南

使用宝塔面板的 **Docker 管理器** + 本项目 `docker/docker-compose.yml` 部署全栈（MySQL / Redis / MinIO / RabbitMQ / ES / 后端 / Nginx）。

同一域名访问，Nginx 按 **User-Agent** 自动分发 PC 端与移动端 H5。

---

## 1. 架构说明

```
用户浏览器
    │
    ▼
宝塔 Nginx（443 HTTPS）
    │  反代
    ▼
Docker: clouddisk-nginx:8080
    ├── 静态页 PC  (frontend/dist)
    ├── 静态页 H5  (mobile/dist/build/h5)  ← UA 自动选择
    ├── /api/*  → clouddisk-backend:8055
    └── /ws/*   → WebSocket

Docker 内网:
    backend ──► mysql / redis / minio / rabbitmq / elasticsearch
```

| 容器 | 作用 | 生产是否暴露公网 |
|------|------|------------------|
| clouddisk-nginx | 前端 + 反代 API | 仅本机 8080（宝塔反代） |
| clouddisk-backend | Spring Boot API | 建议不暴露 |
| clouddisk-mysql | 数据库 | **不要** |
| clouddisk-redis | 缓存 | **不要** |
| clouddisk-minio | 对象存储，桶名 `cloud-disk` | **不要** |
| clouddisk-rabbitmq | 消息队列 | **不要** |
| clouddisk-es | 全文搜索 | **不要** |

---

## 2. 服务器要求

| 项目 | 建议 |
|------|------|
| 系统 | CentOS 7+ / Ubuntu 20.04+ / Debian 11+ |
| 内存 | ≥ 4GB（含 ES 建议 8GB） |
| 磁盘 | ≥ 40GB（视文件量而定） |
| 宝塔 | 7.x 或 8.x |
| 软件 | 宝塔 **Docker 管理器**、**Nginx** |

防火墙 / 安全组仅开放：**22、80、443**。

---

## 3. 宝塔安装 Docker

1. 宝塔面板 → **软件商店** → 搜索 **Docker 管理器** → 安装  
2. 安装完成后 → **Docker** → **设置** → 确认 Docker 服务已启动  
3. （可选）配置镜像加速：国内服务器建议在 Docker 设置里填镜像源  

---

## 4. 上传项目代码

推荐目录：`/www/wwwroot/clouddisk`

### 方式 A：Git

```bash
cd /www/wwwroot
git clone <你的仓库地址> clouddisk
cd clouddisk
```

### 方式 B：宝塔文件管理器

将整个项目压缩包上传到 `/www/wwwroot/clouddisk` 并解压。

目录结构需包含：

```
clouddisk/
├── backend/
├── frontend/
├── mobile/
├── sql/init.sql
└── docker/
    ├── docker-compose.yml
    ├── nginx.conf
    ├── Dockerfile.backend
    └── elasticsearch/Dockerfile
```

---

## 5. 构建前端与后端

在服务器 SSH 或宝塔终端执行：

```bash
cd /www/wwwroot/clouddisk

# 前端 PC
cd frontend
npm ci
npm run build

# 移动端 H5
cd ../mobile
npm ci
npm run build:h5

# 后端 JAR（供 Dockerfile.backend 复制）
cd ../backend
mvn -B package -DskipTests
```

> 若服务器未装 Node / Maven，可在本机 build 后上传 `frontend/dist`、`mobile/dist/build/h5`、`backend/target/clouddisk-pro-*.jar`。

---

## 6. 生产环境配置

### 6.1 创建 `docker/.env.prod`

在 `docker/` 下新建（勿提交弱密码到 Git）：

```env
# 域名（用于 CORS，改成你的）
CLOUDDISK_CORS_ORIGIN=https://disk.example.com

# MySQL
MYSQL_ROOT_PASSWORD=请改成强密码

# MinIO（桶名固定 cloud-disk，由后端自动创建）
MINIO_ROOT_USER=请改成强用户名
MINIO_ROOT_PASSWORD=请改成强密码

# 服务器无需代理，留空即可
HTTP_PROXY=
HTTPS_PROXY=
```

### 6.2 修改 `docker/nginx.conf`

将 `server_name` 改为你的域名：

```nginx
server_name disk.example.com;
```

### 6.3 修改 `docker/docker-compose.yml`（生产必改）

**① 改 MySQL 密码**（与 `.env.prod` 一致）：

```yaml
environment:
  MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-强密码}
healthcheck:
  test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-p${MYSQL_ROOT_PASSWORD:-强密码}"]
```

**② 改 MinIO 账号密码**：

```yaml
environment:
  MINIO_ROOT_USER: ${MINIO_ROOT_USER:-你的用户}
  MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-你的密码}
```

**③ 后端增加 CORS 与 MinIO 密钥**（`backend` 的 `environment`）：

```yaml
CLOUDDISK_MINIO_ACCESS_KEY: ${MINIO_ROOT_USER:-你的用户}
CLOUDDISK_MINIO_SECRET_KEY: ${MINIO_ROOT_PASSWORD:-你的密码}
CLOUDDISK_CORS_ORIGIN: ${CLOUDDISK_CORS_ORIGIN:-https://disk.example.com}
```

**④ 生产环境删除对外端口映射**（仅保留 nginx 8080）：

以下服务的 `ports:` 整段删掉或注释，避免数据库、Redis 等暴露公网：

- `mysql`、`redis`、`minio`、`rabbitmq`、`elasticsearch`
- `backend` 的 `8055:8055`（可选，调试时再开）

保留：

```yaml
nginx:
  ports:
    - "8080:80"
```

**⑤ ES 镜像拉取失败时**，在服务器 build：

```bash
cd /www/wwwroot/clouddisk
docker build -t wu17/disk:elasticsearch-8.12.2-ik-pinyin -f docker/elasticsearch/Dockerfile docker/elasticsearch
```

---

## 7. 启动 Docker Compose

### 方式 A：SSH（推荐）

```bash
cd /www/wwwroot/clouddisk

docker compose --env-file docker/.env.prod \
  -f docker/docker-compose.yml \
  --profile app up -d --build
```

### 方式 B：宝塔 Docker 管理器

1. **Docker** → **Compose** → **添加**
2. 名称：`clouddisk`
3. 路径：`/www/wwwroot/clouddisk/docker/docker-compose.yml`
4. 环境文件：`/www/wwwroot/clouddisk/docker/.env.prod`
5. 启动参数：`--profile app`
6. 点击 **构建并启动**

### 验证

```bash
docker compose -f docker/docker-compose.yml --profile app ps

curl -s http://127.0.0.1:8080 | head
curl -s http://127.0.0.1:8055/actuator/health
```

全部容器应为 `Up`，health 为 `{"status":"UP"}`。

---

## 8. 宝塔 Nginx 绑定域名 + HTTPS

Docker 内 Nginx 监听 **8080**，对外由宝塔提供 **443**。

### 8.1 添加站点

1. **网站** → **添加站点**
2. 域名：`disk.example.com`
3. PHP：选 **纯静态**（或不创建 PHP）

### 8.2 配置反向代理

站点 → **设置** → **反向代理** → **添加反向代理**：

| 项 | 值 |
|----|-----|
| 代理名称 | clouddisk |
| 目标 URL | `http://127.0.0.1:8080` |
| 发送域名 | `$host` |

### 8.3 自定义 Nginx 配置

站点 → **设置** → **配置文件**，在 `server { }` 内补充：

```nginx
client_max_body_size 20g;
proxy_read_timeout 3600s;
proxy_send_timeout 3600s;

location /ws/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_read_timeout 3600s;
}
```

### 8.4 SSL 证书

站点 → **SSL** → **Let's Encrypt** → 申请 → 开启 **强制 HTTPS**。

### 8.5 访问

- PC：`https://disk.example.com`
- 手机：同一地址，Nginx 按 UA 自动加载移动版

---

## 9. 常用运维命令

```bash
cd /www/wwwroot/clouddisk

# 查看状态
docker compose -f docker/docker-compose.yml --profile app ps

# 查看日志
docker compose -f docker/docker-compose.yml --profile app logs -f backend
docker compose -f docker/docker-compose.yml --profile app logs -f nginx

# 停止
docker compose -f docker/docker-compose.yml --profile app down

# 重启单个服务
docker compose -f docker/docker-compose.yml --profile app restart backend
```

---

## 10. 更新发布

```bash
cd /www/wwwroot/clouddisk
git pull

cd frontend && npm run build
cd ../mobile && npm run build:h5
cd ../backend && mvn -B package -DskipTests

cd ..
docker compose --env-file docker/.env.prod \
  -f docker/docker-compose.yml \
  --profile app build backend

docker compose --env-file docker/.env.prod \
  -f docker/docker-compose.yml \
  --profile app up -d
```

前端 `dist` 为 volume 挂载，build 后刷新浏览器即可，无需重建 nginx 镜像。

---

## 11. 数据备份

| 数据 | 位置 |
|------|------|
| MySQL | Docker 卷 `docker_mysql_data` |
| MinIO 文件 | Docker 卷 `docker_minio_data` |
| Redis | Docker 卷 `docker_redis_data` |
| ES 索引 | Docker 卷 `docker_es_data` |

```bash
# 备份 MySQL
docker exec clouddisk-mysql mysqldump -uroot -p cloud_disk > backup_$(date +%F).sql

# 查看卷
docker volume ls | grep docker_
```

建议配合宝塔 **计划任务** 定期备份 SQL 与 MinIO 卷。

---

## 12. 常见问题

### Q1：ES 容器启动失败 / 内存不足

- 调小 `ES_JAVA_OPTS` 为 `-Xms256m -Xmx256m`
- 或暂时注释 `elasticsearch` 服务，并在 backend 环境变量里关闭 ES

### Q2：上传大文件 413

- 宝塔站点 Nginx 与 `docker/nginx.conf` 均需 `client_max_body_size 20g`

### Q3：WebSocket 通知连不上

- 确认宝塔反代配置了 `/ws/` 的 Upgrade 头（见 8.3）

### Q4：视频无封面

- 当前 `Dockerfile.backend` 未包含 **ffmpeg**，视频缩略图/转码需后续在镜像中安装 ffmpeg

### Q5：MinIO 桶不存在

- 桶名固定 **`cloud-disk`**，后端首次连接 MinIO 会自动创建；若仍报错，登录 MinIO 控制台（仅内网）手动创建

### Q6：CORS 跨域错误

- 检查 `CLOUDDISK_CORS_ORIGIN` 是否与访问域名完全一致（含 `https://`）

---

## 13. 快速 Checklist

- [ ] 宝塔安装 Docker 管理器
- [ ] 代码上传到 `/www/wwwroot/clouddisk`
- [ ] `npm run build` + `npm run build:h5` + `mvn package`
- [ ] 创建 `docker/.env.prod`，修改强密码
- [ ] 修改 `nginx.conf` 的 `server_name`
- [ ] 生产环境去掉 mysql/redis/minio 等公网端口
- [ ] `docker compose --profile app up -d --build`
- [ ] 宝塔添加站点 → 反代 `127.0.0.1:8080` → 配置 `/ws/` → 申请 SSL
- [ ] 浏览器访问 `https://你的域名` 测试 PC / 手机

---

## 14. 相关文件

| 文件 | 说明 |
|------|------|
| `docker/docker-compose.yml` | Compose 主文件 |
| `docker/nginx.conf` | 容器内 Nginx（UA 分流 + API 反代） |
| `docker/Dockerfile.backend` | 后端镜像（需先 `mvn package`） |
| `docker/elasticsearch/Dockerfile` | 自定义 ES（IK + 拼音） |
| `backend/.../application-docker.yml` | 后端 `docker` profile 配置 |
| `sql/init.sql` | 数据库初始化 |
