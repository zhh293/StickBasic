# 分享模块说明

## 概述
- 生成唯一分享链接，包含核心参数：帖子ID、分享者ID、分享渠道。
- 解析分享链接，快速返回帖子内容供浏览器渲染。
- 三层解耦：Controller → Service → Mapper；数据库原子更新 `share_count`。
- 高性能：Redis 存储分享令牌与映射；DB 更新异步执行。

## 接口
- 生成分享链接
  - 方法：POST
  - 路径：`/posts/{postId}/share`
  - 参数：`channel`（`qq`、`wechat` 等）
  - 响应：`{ token, url }`
  - 说明：
    - 校验用户身份；布隆过滤器快速判断帖子存在。
    - 生成令牌并写入 Redis（TTL 7 天）。
    - 异步 `incrShareCount` 更新数据库 `share_count`。

- 打开分享链接
  - 方法：GET
  - 路径：`/posts/share/{token}`
  - 响应：帖子详情 `PostListItemVO`
  - 说明：
    - 从 Redis 读取令牌映射，拿到 `postId`。
    - 查询帖子详情与附件、作者/话题信息并返回。

## 数据变更
- 表：`posts`
  - 字段：`share_count` 原子自增。
  - SQL：`UPDATE posts SET share_count = IFNULL(share_count,0) + #{delta} WHERE id = #{id}`。

## 性能与稳定
- 令牌映射存于 Redis Hash，读写 O(1)。
- 布隆过滤器快速判断帖子是否存在，减少不必要 DB 查询。
- 数据库更新通过线程池异步执行，避免阻塞主流程。
- 令牌 TTL 默认 7 天，可按需调整。

## 错误处理
- 令牌过期或不存在：返回错误提示。
- 非法用户或参数缺失：返回校验错误。

## 链接格式
- 相对链接：`/posts/share/{token}`
- 全链接（示例）：`http://localhost:8080/posts/share/{token}`

## 安全
- 令牌仅包含映射信息；不暴露敏感数据。
- 令牌过期自动失效。

## 示例
- 请求：`POST /posts/123/share?channel=wechat`
- 响应：`{"code":1,"msg":"success","data":{"token":"abc123","url":"/posts/share/abc123"}}`