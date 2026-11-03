# 巷里 XIANGRI · 后端

基于 Spring Boot 2.3 的城市生活手边指南后端（店铺 / 秒杀 / 笔记 / 关注 / 用户）。

---

## 一、运行所需环境变量

后端的敏感配置（数据库密码、Redis 密码、第三方 API Key）**全部通过环境变量注入**，
源码与配置文件中不含任何真实密钥。

| 环境变量 | 是否必填 | 说明 | 默认值 |
| --- | --- | --- | --- |
| `DB_URL` | 建议填写 | MySQL JDBC 连接串 | `jdbc:mysql://127.0.0.1:3306/hmdp?useSSL=false&serverTimezone=UTC` |
| `DB_USERNAME` | 建议填写 | MySQL 用户名 | `root` |
| `DB_PASSWORD` | **必填** | MySQL 密码 | 无（必须显式提供） |
| `REDIS_HOST` | 建议填写 | Redis 主机名（容器内建议 `hmdp-redis`） | `127.0.0.1` |
| `REDIS_PORT` | 建议填写 | Redis 端口 | `6379` |
| `REDIS_PASSWORD` | **必填** | Redis 密码；Redis 未开鉴权时可留空 | 无（必须显式提供） |
| `SERVER_PORT` | 否 | 后端监听端口 | `8081` |
| `IMAGE_UPLOAD_DIR` | 否 | 图片上传目录（容器内固定为 `/app/imgs`） | `E:/hm-nginx-1.18.0/html/hmdp/imgs` |
| `DEEPSEEK_API_KEY` | 否 | DeepSeek API Key（当前代码未直接使用，预留） | 空 |
| `DEEPSEEK_BASE_URL` | 否 | DeepSeek 接口地址 | `https://api.deepseek.com` |
| `ALIYUN_ACCESS_KEY_ID` | 否 | 阿里云 AccessKey ID（当前代码未直接使用，预留） | 空 |
| `ALIYUN_ACCESS_KEY_SECRET` | 否 | 阿里云 AccessKey Secret（当前代码未直接使用，预留） | 空 |
| `ALIYUN_REGION` | 否 | 阿里云地域 | `cn-hangzhou` |

更完整的说明见 `src/main/resources/application-example.yaml` 与 `.env.example`。

> Docker Compose 场景下，上传目录通过 `IMAGE_UPLOAD_HOST_DIR`（**宿主机**路径）
> 挂载到容器内 `/app/imgs`，容器内的 `IMAGE_UPLOAD_DIR` 固定为 `/app/imgs`。
> IDE / 命令行直跑时直接设置 `IMAGE_UPLOAD_DIR` 为目标目录即可。

---

## 二、本地启动

### 方式 A：IDE 直接运行

1. 在 IDE 的运行配置中新增上述环境变量（IDEA：`Run/Debug Configurations → Environment variables`）。
2. 直接运行 `com.hmdp.HmDianPingApplication`。

### 方式 B：命令行

```bash
# Windows PowerShell
$env:DB_PASSWORD="<你的 MySQL 密码>"
$env:REDIS_PASSWORD="<你的 Redis 密码>"
mvn -DskipTests spring-boot:run
```

```bash
# Linux / macOS
export DB_PASSWORD='<你的 MySQL 密码>'
export REDIS_PASSWORD='<你的 Redis 密码>'
mvn -DskipTests spring-boot:run
```

### 方式 C：Docker Compose

```bash
cp .env.example .env      # 然后在 .env 中填入真实密码
docker compose up -d
```

`.env` 已被 `.gitignore` 忽略，**不要提交到仓库**。

#### 前置条件：Docker 网络

`docker-compose.yml` 使用了名为 `hmdp-net` 的**外部网络**，首次部署需要先创建：

```bash
docker network create hmdp-net
```

#### 一并启动本地 Redis（可选）

编排文件中的 `redis` 服务默认**不启动**（`profiles: ["local-infra"]`），
避免与已有容器重名。若本机没有现成的 Redis，可加 profile 一起拉起：

```bash
docker compose --profile local-infra up -d
```

此时 `REDIS_HOST` 保持默认的 `hmdp-redis` 即可；使用外部 Redis 时，
请在 `.env` 中把 `REDIS_HOST` 改为该 Redis 的可达地址。

#### 停止与清理

```bash
docker compose down            # 停止容器，保留网络
docker compose --profile local-infra down -v   # 连同 Redis 数据卷一起清理
```

---

## 三、镜像构建

```bash
mvn -DskipTests package
docker build -t xiangri-backend:latest .
```

Dockerfile 内不包含任何密钥，所有敏感配置在运行时通过 `environment` 注入。

---

## 四、数据库初始化

首次部署可导入 `src/main/resources/db/hmdp.sql`（建库 + 初始化演示数据）。
导入命令示例：

```bash
mysql -h 127.0.0.1 -u root -p < src/main/resources/db/hmdp.sql
```

---

## 五、目录结构

```
src/main/java/com/hmdp
├── config/        # MyBatis、Redis(Redisson)、MVC、异常处理配置
├── controller/    # REST 接口
├── dto/           # 请求 / 响应对象
├── entity/        # 数据库实体
├── mapper/        # MyBatis-Plus Mapper
├── service/       # 业务层
└── utils/         # 工具类（缓存、分布式锁、ID 生成器等）

src/main/resources
├── application.yaml            # 公共配置模板（占位符 + 非敏感默认值）
├── application-example.yaml    # 环境变量说明模板
├── db/hmdp.sql                 # 建表与演示数据
├── mapper/                     # MyBatis XML
├── seckill.lua / unlock.lua    # 秒杀与解锁脚本
```

---

## 六、说明

- 秒杀订单通过 Redis Stream（`stream.order`）+ 消费组 `g1` 处理，
  应用启动时通过 `XGROUP CREATE ... MKSTREAM` 幂等创建 Stream 与消费组。
- `spring.redis` 与 Redisson 共用同一组环境变量（`REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD`）。
