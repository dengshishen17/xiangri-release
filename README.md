<div align="center">

# 巷里 XIANGRI

**城市生活手边指南** — 店铺发现 · 限时秒杀 · 笔记分享 · 好友关注

`XIANGRI` = 巷里，把城市里值得一去的地方，收进你随手可翻的清单。

</div>

---

## 关于本项目

巷里是一个「城市生活指南」类全栈项目：前端用 Vue + Element UI 呈现商户发现、笔记与个人主页，
后端用 Spring Boot + Redis 承载秒杀下单、缓存、分布式锁与消息队列等高并发场景。

项目最初脱胎于经典的 hm-dianping 教学项目，在其之上完成了品牌重塑、功能扩展与
工程化改造（环境变量化配置、Docker 化部署、Redis Stream 消费组幂等初始化等）。

---

## 功能一览

| 模块 | 说明 |
| --- | --- |
| 🏪 **商户发现** | 商户列表 / 详情 / 分类 / 地理坐标（Redis GEO）、缓存穿透与空值缓存、逻辑过期缓存、Redis 幂等加载 |
| 🔥 **限时秒杀** | Lua 脚本原子扣减库存、防重复下单、Redis Stream 异步下单、`TransactionTemplate` 保证订单与库存一致性 |
| 📝 **笔记分享** | 笔记发布 / 详情 / 评论、图片上传（本地磁盘 + 分级目录）、防重复提交与关注流 |
| 👥 **好友关注** | 关注 / 取关、共同关注（intersect）、博主探店推荐 |
| 🔐 **用户体系** | 注册 / 登录、MD5 加盐存储、Token 签发与滑动过期、拦截器鉴权 |
| 📊 **数据统计** | 秒杀订单统计、店铺评分、UV 统计（HyperLogLog） |

---

## 技术栈

**后端**

- Java 17（兼容 Java 8 语法级别） / Spring Boot 2.3.12
- MyBatis-Plus 3.4.3 / MySQL 5.7
- Spring Data Redis 2.6.2 / Lettuce 6.1.6 / Redisson 3.13.6
- Redis Stream（`stream.order` + 消费组 `g1`）异步处理秒杀订单
- Lua 脚本（`seckill.lua` / `unlock.lua`）保证原子性
- Maven 打包，Docker 多实例部署 + Nginx 反向代理

**前端**

- Vue 2 + Element UI + Axios
- 原生 HTML / CSS（无构建步骤），静态托管于 Nginx
- 响应式布局，支持移动端

---

## 目录结构

```
xiangri-release/
├── backend/                       # Spring Boot 后端
│   ├── .env.example               # 环境变量示例（复制为 .env 后填入真实值）
│   ├── .gitignore
│   ├── Dockerfile                 # 镜像构建
│   ├── docker-compose.yml         # 双实例编排（8081 / 8082）
│   ├── pom.xml
│   ├── README.md                  # 后端详细说明
│   └── src/
│       ├── main/java/com/hmdp/
│       │   ├── config/            # MyBatis / Redis(Redisson) / MVC / 异常处理
│       │   ├── controller/        # REST 接口
│       │   ├── dto/               # 请求 / 响应对象
│       │   ├── entity/            # 数据库实体
│       │   ├── mapper/            # MyBatis-Plus Mapper
│       │   ├── service/           # 业务层
│       │   └── utils/             # 缓存、分布式锁、ID 生成器等
│       ├── main/resources/
│       │   ├── application.yaml           # 配置模板（占位符 + 非敏感默认值）
│       │   ├── application-example.yaml   # 环境变量说明模板
│       │   ├── db/hmdp.sql                # 建表 + 演示数据
│       │   ├── mapper/VoucherMapper.xml
│       │   ├── seckill.lua
│       │   └── unlock.lua
│       └── test/java/com/hmdp/
│
└── web/                           # 前端静态站点
    ├── index.html                 # 首页（商户发现）
    ├── shop-detail.html           # 商户详情
    ├── shop-list.html             # 商户列表
    ├── blog-detail.html           # 笔记详情
    ├── blog-edit.html             # 发布笔记
    ├── info.html / info-edit.html # 个人主页 / 资料编辑
    ├── other-info.html            # TA 的主页
    ├── login.html / login2.html   # 登录
    ├── css/                       # 样式
    ├── js/                        # Vue / Element / Axios / 公共脚本
    └── imgs/                      # 静态图片资源（含用户上传的笔记配图）
```

---

## 快速开始

### 一、环境要求

- JDK 17（或 JDK 8+）
- Maven 3.6+
- MySQL 5.7+
- Redis 6+
- Docker + Docker Compose（若使用容器化部署）
- Nginx（前端静态托管与反向代理）

### 二、准备数据库

```bash
cd backend
mysql -h 127.0.0.1 -u root -p < src/main/resources/db/hmdp.sql
```

该脚本包含建表语句与演示数据（商户、笔记、分类、优惠券等）。

### 三、配置后端

所有敏感配置通过**环境变量**注入，仓库内不含任何真实密码或密钥。

```bash
cd backend
cp .env.example .env      # 在 .env 中填入真实的 DB_PASSWORD / REDIS_PASSWORD
```

> `.env` 已被 `.gitignore` 忽略，**请勿提交到仓库**。

环境变量清单见 [`backend/README.md`](backend/README.md) 或
[`backend/src/main/resources/application-example.yaml`](backend/src/main/resources/application-example.yaml)。

### 四、启动后端

**方式 A · IDE**
在运行配置中配置好环境变量后，直接运行 `com.hmdp.HmDianPingApplication`。

**方式 B · 命令行**

```bash
cd backend
export DB_PASSWORD='<你的 MySQL 密码>'
export REDIS_PASSWORD='<你的 Redis 密码>'
mvn -DskipTests spring-boot:run
```

**方式 C · Docker Compose（推荐）**

```bash
cd backend

# 1. 创建外部网络（首次）
docker network create hmdp-net

# 2. 准备配置
cp .env.example .env    # 填入真实密码

# 3. 构建镜像
mvn -DskipTests package
docker build -t xiangri-backend:latest .

# 4. 启动
docker compose up -d
```

编排文件默认启动 `backend-8081` 与 `backend-8082` 两个实例。
若本机没有现成 Redis，可一并拉起内置的 Redis：

```bash
docker compose --profile local-infra up -d
```

### 五、部署前端

前端为纯静态站点，将 `web/` 目录部署到 Nginx 站点根目录即可。

参考 Nginx 配置（`/api` 反向代理到后端双实例）：

```nginx
server {
    listen       8080;
    server_name  localhost;

    location / {
        root   html/hmdp;   # 指向本仓库的 web/ 目录
        index  index.html index.htm;
    }

    location /api {
        rewrite /api(/.*) $1 break;
        proxy_pass http://backend;
    }

    upstream backend {
        server 127.0.0.1:8081 max_fails=5 fail_timeout=10s weight=1;
        server 127.0.0.1:8082 max_fails=5 fail_timeout=10s weight=1;
    }
}
```

### 六、验证

```bash
curl http://localhost:8081/shop/1     # 后端直连
curl http://localhost:8080/api/shop/1 # 经 Nginx 反代
```

---

## 安全说明

本项目所有敏感配置均已从源码与配置文件中移除，改为通过环境变量注入：

- MySQL 密码 → `DB_PASSWORD`
- Redis 密码 → `REDIS_PASSWORD`
- 第三方服务密钥（DeepSeek / 阿里云）→ 预留占位符，当前代码未直接使用
- Redis Stream 消费组通过 `XGROUP CREATE ... MKSTREAM` 幂等创建，
  不写入任何初始化占位消息

仓库中不含任何真实密码、API Key 或私钥。`src/main/resources/db/hmdp.sql`
中的用户数据为上游教学项目的**演示种子数据**：手机号为连续生成的示例号段
（`136886688xx` 等），密码字段全部为空，昵称为随机串或通用昵称，
**不包含任何真实个人信息**。

---

## 说明

- 本项目为学习与演示用途，数据均为公开演示数据。
- 欢迎提交 Issue 与 Pull Request。

## License

[MIT](LICENSE) © 巷里 XIANGRI
