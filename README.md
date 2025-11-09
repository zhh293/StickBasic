# BasicBack 后端服务

一个以 Spring Boot 为核心、集成 PostgreSQL、Redis、MyBatis、JWT 安全、二级缓存（Caffeine + Redis）、WebSocket、AI 能力、对象存储（AliOSS）的通用论坛/社交型后端。支持用户注册与登录、资料管理、软删除、关注/取消关注、帖子/话题、附件、搜索、邮件等功能。

## 项目特性
- 用户体系：注册、登录、JWT 鉴权、资料查询/更新、软删除、修改密码。
- 社交关系：关注/取消关注、关注列表/粉丝列表、数量统计、是否已关注检查（Redis 集合 + MySQL 落库，响应快）。
- 内容模块：帖子（Posts）、话题（Topic）、置顶（Stick）等常见论坛能力。
- 搜索模块：统一搜索接口（`SearchController`）。
- 附件模块：支持文件上传与 AliOSS 存储记录（`AttachmentService`）。
- 邮件模块：邮件发送、评论与收件箱（`MailController` 相关 Mapper）。
- AI 模块：对话/嵌入（Spring AI），可扩展检索（PDF/本地文件仓库）。
- 实时能力：内置 WebSocket 服务，支持在线消息推送。
- 缓存体系：自研二级缓存（`Redis + Caffeine`）组合，支持手动/全量一级缓存策略，防穿透、过期刷新等。
- 工具集：分布式 ID、JWT、统一异常处理、常用工具类与模板封装。

## 中间件与服务
- 数据库：`PostgreSQL`（默认库名 `basic`）。
- 缓存：`Redis`（核心依赖）与 `Caffeine`（进程内一级缓存）。
- 消息队列：`RabbitMQ`（在 `com.tmd.config.RabbitMQConfig` 和 `publisher/consumer` 中，可选）。
- 搜索：`Elasticsearch`（`com.tmd.config.ESConfig`，可选，适用于全文检索扩展）。
- 云存储：`AliOSS`（`com.tmd.properties.AliOssProperties`，附件记录与对象存储）。
- AI 能力：`Spring AI`（OpenAI 兼容，`spring.ai.openai.*`），对话与嵌入。
- 实时通信：`WebSocket`（`WebSocket/WebSocketServer.java`）。
- 组合缓存：`Redis + Caffeine` 自定义 CacheManager（`spring/RedisCaffeineCacheManager.java`）。
- 缓存同步：Redis 发布/订阅（`sync/CacheMessageListener.java`），用于多节点缓存失效通知。

### 本地启动清单（建议）
- 必需：
  - PostgreSQL（创建并配置 `basic` 数据库）。
  - Redis（默认 `localhost:6379`）。
- 可选：
  - RabbitMQ（本地默认 `amqp://guest:guest@localhost:5672/`，如不需要可不启）。
  - Elasticsearch（如启用搜索扩展）。
  - AliOSS（线上或本地模拟，附件模块依赖；可降级为仅记录 URL）。
  - Spring AI（若使用 AI 接口，需准备 API Key）。

### 启用/关闭中间件
- RabbitMQ：
  - 查看 `com.tmd.config.RabbitMQConfig`、`publisher/MessageProducer`、`consumer/MessageConsumer`。
  - 若不启用，可保持相关 Bean 未被引用或在配置中关闭队列声明；业务中避免调用 Producer。
- Elasticsearch：
  - 查看 `com.tmd.config.ESConfig` 与 `SearchController`；未启用时不要注入 ES 相关 Bean。
- AliOSS：
  - 在 `application.yml` 的 `sky.alioss.*` 提供配置；若没有真实 OSS，可只记录附件元数据。
- Spring AI：
  - 在 `application.yml` 设置 `spring.ai.openai.*`；建议使用环境变量覆盖 `api-key`。
- 二级缓存（Redis + Caffeine）：
  - 通过 `l2cache.config.*` 控制策略（是否启用一级缓存、过期、手动 Key/CacheName）。

### 关键配置与默认端口
- PostgreSQL：`localhost:5432`（`spring.datasource.url`）。
- Redis：`localhost:6379`（`spring.data.redis.*`）。
- RabbitMQ：`localhost:5672`（如启用）。
- Elasticsearch：`localhost:9200`（如启用）。
- WebSocket：`ws://localhost:8080`（默认端口同应用）。
- 应用：`server.port=8080`。


## 技术栈
- 后端框架：`Spring Boot`
- 数据访问：`MyBatis`（Mapper + XML），`PageHelper`
- 数据库：`PostgreSQL`
- 缓存：`Redis`，`Caffeine`
- 安全：`Spring Security` + `JWT`
- 实时：`WebSocket`
- 云存储：`AliOSS`
- AI：`Spring AI`（OpenAI 兼容接口，可配置模型与向量嵌入）

## 目录结构
```
src/main/java/com/tmd
├── BasicBackApplication.java               # 启动类
├── config/                                 # 框架与组件配置
│   ├── CacheRedisCaffeineAutoConfiguration.java
│   ├── L2CacheConfig.java                  # 二级缓存配置对象
│   ├── L2CacheProperties.java              # 绑定 application.yml 的 l2cache.*
│   ├── RedisConfig.java                    # Redis 连接与序列化
│   ├── SecurityConfig.java                 # Spring Security 配置
│   ├── RabbitMQConfig.java                 # （可选）消息组件配置
│   └── ...
├── controller/                             # 各业务接口
│   ├── UserController.java                 # 用户注册/登录/资料/关注
│   ├── PostsController.java                # 帖子相关
│   ├── TopicController.java                # 话题相关
│   ├── StickController.java                # 置顶相关
│   ├── SearchController.java               # 搜索
│   ├── CommonController.java               # 公共能力
│   └── AiController.java                   # AI 能力
├── entity/                                 # DTO/PO 等实体
├── mapper/                                 # MyBatis Mapper 接口
│   ├── UserMapper.java
│   ├── FollowMapper.java
│   └── ...
├── service/                                # Service 接口与实现
│   ├── FollowService.java
│   └── impl/                               # 业务实现（例如 FollowServiceImpl）
├── spring/                                 # 自定义 CacheManager 等
├── tools/                                  # 工具类（JWT、ID等）
└── WebSocket/                              # WebSocket 服务端
```

## 配置说明
主要配置位于 `src/main/resources/application.yml`：
- `server.port`: 应用端口（默认 `8080`）。
- `spring.datasource`: PostgreSQL 连接信息（默认 `jdbc:postgresql://localhost:5432/basic`）。
- `spring.data.redis`: Redis 连接信息（默认 `localhost:6379`）。
- `Bluegod.security.jwt-key`: JWT 密钥与过期时间。
- `mybatis.mapper-locations`: `classpath:mapper/*.xml`。
- `pagehelper.helper-dialect`: 请与实际数据库保持一致（建议设为 `postgresql`）。
- `sky.alioss`: AliOSS 相关配置（用于附件存储记录）。
- `spring.ai.openai`: API Key 与兼容 `base-url`（敏感信息建议用环境变量覆盖，不要写死）。
- `l2cache.config`: 二级缓存组合与 Caffeine/Redis 参数，支持手动/全量一级缓存。

二级缓存 IDE 警告消除建议：
- 确保 `L2CacheProperties` 加了 `@Component` 并可被扫描注册。
- `pom.xml` 添加 `spring-boot-configuration-processor` 以生成配置元数据，IDE 将识别 `l2cache.*` 键。

## 启动前准备
- 安装并启动 `PostgreSQL`（创建数据库 `basic`），导入所需表结构（例如 `attachment_table.sql`，以及用户/关注/帖子等表）。
- 安装并启动 `Redis`（默认端口 `6379`）。
- 准备好 `AliOSS` 存储配置（如使用附件功能）。
- 如果使用 AI 功能，准备 `OpenAI API Key` 并通过环境变量覆盖：
  - Windows PowerShell: `setx SPRING_AI_OPENAI_API_KEY "your-key"`
  - 或 `JAVA_TOOL_OPTIONS`：`-Dspring.ai.openai.api-key=...`

## 快速启动
开发模式：
- `mvn spring-boot:run`

生产构建：
- `mvn clean package`
- `java -jar target/BasicBack-*.jar`

默认服务监听 `http://localhost:8080/`。

## API 速览（选摘）
以下为部分常用接口，详细以 Controller 代码与接口文档为准。

用户认证与资料
- `POST /register`：用户注册，返回 `UserVO`。
- `POST /login`：登录成功返回 `token`（`Authorization: Bearer <token>`）。
- `GET /user/profile`：获取当前用户资料。
- `PUT /user/profile`：更新资料（头像与背景图会写入附件记录）。
- `PUT /updatepw`：修改密码（需要 `Authorization`）。
- `DELETE /{userId}`：软删除用户。

关注关系（需登录）
- `POST /{userId}/follow`：关注指定用户。
- `DELETE /{userId}/follow`：取消关注指定用户。
- `GET /user/{userId}/following`：获取关注列表（用户 ID 列表）。
- `GET /user/{userId}/followers`：获取粉丝列表（用户 ID 列表）。
- `GET /user/{userId}/following/count`：关注数。
- `GET /user/{userId}/followers/count`：粉丝数。
- `GET /user/{userId}/is-following`：当前用户是否关注 `userId`。

内容、搜索与其它
- `PostsController`/`TopicController`/`StickController`：帖子、话题、置顶相关能力。
- `SearchController`：搜索接口。
- `CommonController`：通用能力（如健康检查、公共数据等）。
- `AiController`：AI 对话/嵌入能力。
- `MailController` 与相关 Mapper：邮件模块。

## 运行机制与亮点
- 组合缓存：
  - 一级缓存（Caffeine）+ 二级缓存（Redis），可通过 `l2cache.config.composite` 策略灵活切换。
  - 支持允许空值、过期时间、多 CacheName 精细化过期策略。
- 关注关系：
  - Redis 集合（`follow:{followerId}` / `followers:{followingId}`）提供 O(1) 响应；
  - 后台异步写入 MySQL，保证数据持久性与一致性；
  - 计数缓存键：`follow_count:{userId}` / `follower_count:{userId}`。
- 安全：
  - 基于 `JWT` 的鉴权，`Authorization: Bearer <token>`；
  - 过滤器 `JwtAuthenticationTokenFilter` 与统一异常处理。
- 实时通信：
  - `WebSocketServer` 支持推送消息；
- 可扩展：
  - AI 接入采用 Spring AI，可替换模型与兼容 API。
  - RabbitMQ 配置在工程中，业务可选用（当前关注实现未依赖消息中间件）。

## 常见问题（FAQ）
- `application.yml` 的 `l2cache.*` 在 IDE 标黄：
  - 给 `L2CacheProperties` 加 `@Component`，并引入 `spring-boot-configuration-processor`；
  - 或使用 `@ConfigurationPropertiesScan`/`@EnableConfigurationProperties` 注册属性 Bean。
- 数据库方言：
  - 你的数据源是 PostgreSQL，但 `pagehelper.helper-dialect` 配为 `mysql`，建议改为 `postgresql` 以避免分页方言不一致。
- 敏感配置：
  - `spring.ai.openai.api-key` 建议用环境变量覆盖，不要在仓库中明文保存真实密钥。
- Redis 键过期：
  - 关注集合与计数键默认 7 天过期，可按业务需要调整或改为永不过期。

## 开发约定
- 接口返回统一使用 `Result` 封装。
- 控制层只做入参校验与调度，业务逻辑在 `service` 实现。
- 数据访问统一通过 Mapper 与 XML，必要时走注解 SQL。
- 缓存 Key 命名：`follow:{userId}`、`followers:{userId}`、`follow_count:{userId}`、`follower_count:{userId}`。
- JWT 校验通过 `Authorization` 请求头，统一使用工具类 `JwtUtil` 与 `BaseContext` 获取用户。

## 贡献与测试
- 单元测试位于 `src/test/java`，欢迎补充更多模块测试。
- 提交前请确保项目构建通过并进行基本接口回归。

---
如需进一步的部署文档、接口清单或架构图，请告知我具体模块需求，我可以继续完善对应章节。