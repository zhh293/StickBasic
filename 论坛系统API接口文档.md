# 论坛系统 API 接口文档

## 项目概述

这是一个基于 Spring Boot + Gateway + Nacos + Redis + ES + RabbitMQ 的现代化论坛系统，支持故事帖子、日签帖子、匿名邮件等核心功能。

## 技术栈

- **网关层**: Spring Cloud Gateway + Nacos (路由、XSS防护、SQL注入防护、身份认证、限流、黑白名单)
- **缓存层**: Redis (缓存穿透/击穿防护、点赞缓存、邮箱推送)
- **搜索引擎**: Elasticsearch (全文搜索、地理位置计算)
- **消息队列**: RabbitMQ (AI生成削峰、邮箱发送削峰)

## 数据库设计

### 用户表 (user)
```sql
CREATE TABLE user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    avatar VARCHAR(255),
    daily_bookmark VARCHAR(500),  -- 每日书签
    homepage_background VARCHAR(255),  -- 主页背景
    account_days INT DEFAULT 0,  -- 起号天数
    personal_signature VARCHAR(200),  -- 个人签名
    status ENUM('active', 'banned', 'deleted') DEFAULT 'active',  -- 用户状态
    deleted_at TIMESTAMP NULL,  -- 软删除时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 帖子表 (post)
```sql
CREATE TABLE post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    topic_id BIGINT,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    post_type ENUM('story', 'daily_sign') NOT NULL,  -- 故事帖子/日签帖子
    status ENUM('draft', 'published', 'deleted') DEFAULT 'published',  -- 帖子状态
    like_count INT DEFAULT 0,
    collect_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    share_count INT DEFAULT 0,
    view_count INT DEFAULT 0,  -- 浏览数
    publish_location VARCHAR(100),  -- 发布地点
    latitude DECIMAL(10, 8),  -- 纬度
    longitude DECIMAL(11, 8),  -- 经度
    deleted_at TIMESTAMP NULL,  -- 软删除时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id),
    FOREIGN KEY (topic_id) REFERENCES topic(id)
);
```

### 话题表 (topic)
```sql
CREATE TABLE topic (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    cover_image VARCHAR(255),
    post_count INT DEFAULT 0,
    follower_count INT DEFAULT 0,  -- 关注话题的用户数
    status ENUM('pending', 'approved', 'rejected') DEFAULT 'pending',
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES user(id)
);
```

### 帖子附件表 (post_attachment)
```sql
CREATE TABLE post_attachment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    file_size BIGINT,
    file_name VARCHAR(255),  -- 原始文件名
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (post_id) REFERENCES post(id)
);
```

### 收藏表 (favorite)
```sql
CREATE TABLE favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id),
    FOREIGN KEY (post_id) REFERENCES post(id),
    UNIQUE KEY uk_user_post (user_id, post_id)
);
```

### 评论表 (comment)
```sql
CREATE TABLE comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    parent_id BIGINT,  -- 父评论ID
    reply_to_user_id BIGINT,  -- 回复的用户ID
    content TEXT NOT NULL,
    like_count INT DEFAULT 0,
    reply_count INT DEFAULT 0,  -- 回复数
    status ENUM('normal', 'deleted') DEFAULT 'normal',  -- 评论状态
    deleted_at TIMESTAMP NULL,  -- 软删除时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id),
    FOREIGN KEY (post_id) REFERENCES post(id),
    FOREIGN KEY (parent_id) REFERENCES comment(id),
    FOREIGN KEY (reply_to_user_id) REFERENCES user(id)
);
```

### 关注表 (follow)
```sql
CREATE TABLE follow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    follower_id BIGINT NOT NULL,  -- 关注者
    following_id BIGINT NOT NULL,  -- 被关注者
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (follower_id) REFERENCES user(id),
    FOREIGN KEY (following_id) REFERENCES user(id),
    UNIQUE KEY uk_follow (follower_id, following_id)
);
```

### 邮件表 (mail)
```sql
CREATE TABLE mail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sender_id BIGINT NOT NULL,
    stamp_type VARCHAR(50) NOT NULL,  -- 邮票类型
    stamp_content VARCHAR(500),  -- 邮票内容
    sender_nickname VARCHAR(50) NOT NULL,
    recipient_email VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    status ENUM('sent', 'delivered', 'read') DEFAULT 'sent',  -- 邮件状态
    read_at TIMESTAMP NULL,  -- 阅读时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sender_id) REFERENCES user(id)
);
```

### 邮件评论表 (mail_comment)
```sql
CREATE TABLE mail_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mail_id BIGINT NOT NULL,
    commenter_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (mail_id) REFERENCES mail(id),
    FOREIGN KEY (commenter_id) REFERENCES user(id)
);
```

### 收到邮件表 (received_mail)
```sql
CREATE TABLE received_mail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recipient_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    stamp_type VARCHAR(50) NOT NULL,
    sender_nickname VARCHAR(50) NOT NULL,
    original_mail_id BIGINT,  -- 原始邮件ID
    status ENUM('unread', 'read') DEFAULT 'unread',  -- 阅读状态
    read_at TIMESTAMP NULL,  -- 阅读时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (recipient_id) REFERENCES user(id),
    FOREIGN KEY (sender_id) REFERENCES user(id),
    FOREIGN KEY (original_mail_id) REFERENCES mail(id)
);
```

### 点赞记录表 (like_record)
```sql
CREATE TABLE like_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    target_type ENUM('post', 'comment') NOT NULL,
    target_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id),
    UNIQUE KEY uk_user_target (user_id, target_type, target_id)
);
```

### 话题关注表 (topic_follow)
```sql
CREATE TABLE topic_follow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id),
    FOREIGN KEY (topic_id) REFERENCES topic(id),
    UNIQUE KEY uk_user_topic (user_id, topic_id)
);
```

### 系统配置表 (system_config)
```sql
CREATE TABLE system_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(100) UNIQUE NOT NULL,
    config_value TEXT,
    description VARCHAR(500),
    config_type ENUM('string', 'number', 'boolean', 'json') DEFAULT 'string',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 用户行为日志表 (user_behavior_log)
```sql
CREATE TABLE user_behavior_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    action_type VARCHAR(50) NOT NULL,  -- 行为类型：view, like, comment, share等
    target_type VARCHAR(50),  -- 目标类型：post, comment, topic等
    target_id BIGINT,  -- 目标ID
    ip_address VARCHAR(45),  -- IP地址
    user_agent TEXT,  -- 用户代理
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id),
    INDEX idx_user_action (user_id, action_type),
    INDEX idx_target (target_type, target_id)
);
```

## API 接口文档

### 1. 用户管理模块

#### 1.1 用户注册
```http
POST /api/user/register
Content-Type: application/json

{
    "username": "string",
    "password": "string",
    "email": "string",
    "avatar": "string"
}
```

**响应示例:**
```json
{
    "code": 200,
    "message": "注册成功",
    "data": {
        "userId": 1,
        "username": "testuser",
        "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    }
}
```

#### 1.2 用户登录
```http
POST /api/user/login
Content-Type: application/json

{
    "username": "string",
    "password": "string"
}
```

#### 1.3 获取用户信息
```http
GET /api/user/profile
Authorization: Bearer {token}
```

**响应示例:**
```json
{
    "code": 200,
    "data": {
        "userId": 1,
        "username": "testuser",
        "email": "test@example.com",
        "avatar": "https://example.com/avatar.jpg",
        "dailyBookmark": "今日书签内容",
        "homepageBackground": "https://example.com/bg.jpg",
        "accountDays": 365,
        "personalSignature": "个人签名",
        "status": "active",
        "createdAt": "2024-01-01T00:00:00Z",
        "updatedAt": "2024-01-15T10:30:00Z"
    }
}
```

#### 1.4 更新用户信息
```http
PUT /api/user/profile
Authorization: Bearer {token}
Content-Type: application/json

{
    "dailyBookmark": "string",
    "homepageBackground": "string",
    "personalSignature": "string",
    "avatar": "string"
}
```

#### 1.5 用户状态管理
```http
PUT /api/user/{userId}/status
Authorization: Bearer {token}
Content-Type: application/json

{
    "status": "active|banned|deleted"
}
```

#### 1.6 软删除用户
```http
DELETE /api/user/{userId}
Authorization: Bearer {token}
```

#### 1.7 获取用户统计信息
```http
GET /api/user/stats
Authorization: Bearer {token}
```

**响应示例:**
```json
{
    "code": 200,
    "data": {
        "accountDays": 365,
        "postCount": 25,
        "followerCount": 120,
        "followingCount": 80,
        "totalLikes": 1500,
        "totalViews": 5000,
        "topicFollowCount": 15
    }
}
```

### 2. 帖子管理模块

#### 2.1 获取帖子列表
```http
GET /api/posts?page=1&size=10&type=story&topicId=1&sortBy=latest&status=published
Authorization: Bearer {token}
```

**查询参数:**
- `page`: 页码 (默认1)
- `size`: 每页数量 (默认10)
- `type`: 帖子类型 (story/daily_sign)
- `topicId`: 话题ID (可选)
- `sortBy`: 排序方式 (latest/popular/hot/views)
- `status`: 帖子状态 (published/draft/deleted)

**响应示例:**
```json
{
    "code": 200,
    "data": {
        "content": [
            {
                "postId": 1,
                "title": "帖子标题",
                "content": "帖子内容",
                "type": "story",
                "status": "published",
                "likeCount": 25,
                "collectCount": 10,
                "commentCount": 8,
                "shareCount": 5,
                "viewCount": 150,
                "publishLocation": "北京市",
                "latitude": 39.9042,
                "longitude": 116.4074,
                "author": {
                    "userId": 1,
                    "username": "author",
                    "avatar": "https://example.com/avatar.jpg"
                },
                "topic": {
                    "topicId": 1,
                    "name": "话题名称"
                },
                "attachments": [
                    {
                        "fileUrl": "https://example.com/image.jpg",
                        "fileType": "image",
                        "fileName": "image.jpg"
                    }
                ],
                "createdAt": "2024-01-15T10:30:00Z",
                "updatedAt": "2024-01-15T10:30:00Z"
            }
        ],
        "totalElements": 100,
        "totalPages": 10,
        "currentPage": 1
    }
}
```

#### 2.2 获取帖子详情
```http
GET /api/posts/{postId}
Authorization: Bearer {token}
```

**响应示例:**
```json
{
    "code": 200,
    "data": {
        "postId": 1,
        "title": "帖子标题",
        "content": "帖子内容",
        "type": "story",
        "status": "published",
        "likeCount": 25,
        "collectCount": 10,
        "commentCount": 8,
        "shareCount": 5,
        "viewCount": 150,
        "publishLocation": "北京市",
        "latitude": 39.9042,
        "longitude": 116.4074,
        "isLiked": true,
        "isCollected": false,
        "author": {
            "userId": 1,
            "username": "author",
            "avatar": "https://example.com/avatar.jpg",
            "personalSignature": "个人签名"
        },
        "topic": {
            "topicId": 1,
            "name": "话题名称",
            "coverImage": "https://example.com/topic.jpg"
        },
        "attachments": [
            {
                "fileUrl": "https://example.com/image.jpg",
                "fileType": "image",
                "fileName": "image.jpg",
                "fileSize": 1024000
            }
        ],
        "createdAt": "2024-01-15T10:30:00Z",
        "updatedAt": "2024-01-15T10:30:00Z"
    }
}
```

#### 2.3 创建帖子
```http
POST /api/posts
Authorization: Bearer {token}
Content-Type: application/json

{
    "title": "string",
    "content": "string",
    "type": "story",
    "topicId": 1,
    "publishLocation": "string",
    "latitude": 39.9042,
    "longitude": 116.4074,
    "status": "draft",
    "attachments": [
        {
            "fileUrl": "string",
            "fileType": "image",
            "fileName": "string"
        }
    ]
}
```

#### 2.4 更新帖子
```http
PUT /api/posts/{postId}
Authorization: Bearer {token}
Content-Type: application/json

{
    "title": "string",
    "content": "string",
    "topicId": 1,
    "publishLocation": "string",
    "latitude": 39.9042,
    "longitude": 116.4074,
    "status": "published"
}
```

#### 2.5 软删除帖子
```http
DELETE /api/posts/{postId}
Authorization: Bearer {token}
```

#### 2.6 恢复已删除帖子
```http
POST /api/posts/{postId}/restore
Authorization: Bearer {token}
```

#### 2.7 记录帖子浏览
```http
POST /api/posts/{postId}/view
Authorization: Bearer {token}
```

#### 2.8 点赞/取消点赞
```http
POST /api/posts/{postId}/like
Authorization: Bearer {token}
```

#### 2.9 收藏/取消收藏
```http
POST /api/posts/{postId}/favorite
Authorization: Bearer {token}
```

#### 2.10 分享帖子
```http
POST /api/posts/{postId}/share
Authorization: Bearer {token}
```

### 3. 话题管理模块

#### 3.1 获取话题列表
```http
GET /api/topics?page=1&size=20&status=approved&sortBy=popular
```

**查询参数:**
- `page`: 页码 (默认1)
- `size`: 每页数量 (默认20)
- `status`: 话题状态 (pending/approved/rejected)
- `sortBy`: 排序方式 (latest/popular/followers)

**响应示例:**
```json
{
    "code": 200,
    "data": {
        "content": [
            {
                "topicId": 1,
                "name": "话题名称",
                "description": "话题描述",
                "coverImage": "https://example.com/topic.jpg",
                "postCount": 150,
                "followerCount": 1200,
                "status": "approved",
                "isFollowed": false,
                "createdBy": {
                    "userId": 1,
                    "username": "creator"
                },
                "createdAt": "2024-01-01T00:00:00Z",
                "updatedAt": "2024-01-15T10:30:00Z"
            }
        ],
        "totalElements": 50,
        "totalPages": 3,
        "currentPage": 1
    }
}
```

#### 3.2 获取话题详情
```http
GET /api/topics/{topicId}
Authorization: Bearer {token}
```

#### 3.3 创建话题申请
```http
POST /api/topics
Authorization: Bearer {token}
Content-Type: application/json

{
    "name": "string",
    "description": "string",
    "coverImage": "string"
}
```

#### 3.4 更新话题
```http
PUT /api/topics/{topicId}
Authorization: Bearer {token}
Content-Type: application/json

{
    "name": "string",
    "description": "string",
    "coverImage": "string"
}
```

#### 3.5 关注/取消关注话题
```http
POST /api/topics/{topicId}/follow
Authorization: Bearer {token}
```

#### 3.6 获取话题下的帖子
```http
GET /api/topics/{topicId}/posts?page=1&size=10&sortBy=latest
```

#### 3.7 获取话题关注者列表
```http
GET /api/topics/{topicId}/followers?page=1&size=20
```

#### 3.8 审核话题申请
```http
PUT /api/topics/{topicId}/status
Authorization: Bearer {token}
Content-Type: application/json

{
    "status": "approved|rejected",
    "reason": "审核原因"
}
```

### 4. 评论管理模块

#### 4.1 获取评论列表
```http
GET /api/posts/{postId}/comments?page=1&size=10&sortBy=latest
```

**查询参数:**
- `page`: 页码 (默认1)
- `size`: 每页数量 (默认10)
- `sortBy`: 排序方式 (latest/popular)

**响应示例:**
```json
{
    "code": 200,
    "data": {
        "content": [
            {
                "commentId": 1,
                "content": "评论内容",
                "likeCount": 5,
                "replyCount": 2,
                "status": "normal",
                "author": {
                    "userId": 1,
                    "username": "commenter",
                    "avatar": "https://example.com/avatar.jpg"
                },
                "replies": [
                    {
                        "commentId": 2,
                        "content": "回复内容",
                        "likeCount": 1,
                        "replyToUser": {
                            "userId": 2,
                            "username": "replied_user"
                        },
                        "author": {
                            "userId": 3,
                            "username": "replier",
                            "avatar": "https://example.com/avatar2.jpg"
                        },
                        "createdAt": "2024-01-15T11:00:00Z"
                    }
                ],
                "isLiked": false,
                "createdAt": "2024-01-15T10:30:00Z",
                "updatedAt": "2024-01-15T10:30:00Z"
            }
        ],
        "totalElements": 25,
        "totalPages": 3,
        "currentPage": 1
    }
}
```

#### 4.2 发表评论
```http
POST /api/posts/{postId}/comments
Authorization: Bearer {token}
Content-Type: application/json

{
    "content": "string",
    "parentId": 0,
    "replyToUserId": 0
}
```

#### 4.3 更新评论
```http
PUT /api/comments/{commentId}
Authorization: Bearer {token}
Content-Type: application/json

{
    "content": "string"
}
```

#### 4.4 软删除评论
```http
DELETE /api/comments/{commentId}
Authorization: Bearer {token}
```

#### 4.5 恢复已删除评论
```http
POST /api/comments/{commentId}/restore
Authorization: Bearer {token}
```

#### 4.6 点赞评论
```http
POST /api/comments/{commentId}/like
Authorization: Bearer {token}
```

### 5. 关注管理模块

#### 5.1 关注用户
```http
POST /api/user/{userId}/follow
Authorization: Bearer {token}
```

#### 5.2 取消关注
```http
DELETE /api/user/{userId}/follow
Authorization: Bearer {token}
```

#### 5.3 获取关注列表
```http
GET /api/user/{userId}/following?page=1&size=20
```

#### 5.4 获取粉丝列表
```http
GET /api/user/{userId}/followers?page=1&size=20
```

### 6. 邮件管理模块

#### 6.1 发送匿名邮件
```http
POST /api/mails
Authorization: Bearer {token}
Content-Type: application/json

{
    "stampType": "love",
    "stampContent": "string",
    "senderNickname": "string",
    "recipientEmail": "string",
    "content": "string"
}
```

#### 6.2 获取邮件列表
```http
GET /api/mails?page=1&size=10&status=sent
Authorization: Bearer {token}
```

**查询参数:**
- `page`: 页码 (默认1)
- `size`: 每页数量 (默认10)
- `status`: 邮件状态 (sent/delivered/read)

#### 6.3 获取邮件详情
```http
GET /api/mails/{mailId}
Authorization: Bearer {token}
```

**响应示例:**
```json
{
    "code": 200,
    "data": {
        "mailId": 1,
        "stampType": "love",
        "stampContent": "邮票内容",
        "senderNickname": "匿名用户",
        "recipientEmail": "recipient@example.com",
        "content": "邮件内容",
        "status": "read",
        "readAt": "2024-01-15T10:30:00Z",
        "commentCount": 3,
        "comments": [
            {
                "commentId": 1,
                "content": "评论内容",
                "commenter": {
                    "userId": 2,
                    "username": "commenter"
                },
                "createdAt": "2024-01-15T11:00:00Z"
            }
        ],
        "createdAt": "2024-01-15T10:00:00Z"
    }
}
```

#### 6.4 更新邮件状态
```http
PUT /api/mails/{mailId}/status
Authorization: Bearer {token}
Content-Type: application/json

{
    "status": "delivered|read"
}
```

#### 6.5 评论邮件
```http
POST /api/mails/{mailId}/comments
Authorization: Bearer {token}
Content-Type: application/json

{
    "content": "string"
}
```

#### 6.6 获取收到的邮件
```http
GET /api/mails/received?page=1&size=10&status=unread
Authorization: Bearer {token}
```

**查询参数:**
- `page`: 页码 (默认1)
- `size`: 每页数量 (默认10)
- `status`: 阅读状态 (unread/read)

#### 6.7 标记邮件为已读
```http
PUT /api/mails/received/{receivedMailId}/read
Authorization: Bearer {token}
```

### 7. 搜索模块

#### 7.1 全文搜索
```http
GET /api/search?keyword=关键词&type=all&page=1&size=10
Authorization: Bearer {token}
```

**查询参数:**
- `keyword`: 搜索关键词
- `type`: 搜索类型 (all/post/topic/user)
- `page`: 页码
- `size`: 每页数量

#### 7.2 地理位置搜索
```http
GET /api/search/nearby?lat=39.9042&lng=116.4074&radius=5000&page=1&size=10
Authorization: Bearer {token}
```

### 8. 点赞管理模块

#### 8.1 获取用户点赞记录
```http
GET /api/likes?page=1&size=20&targetType=post
Authorization: Bearer {token}
```

**查询参数:**
- `page`: 页码 (默认1)
- `size`: 每页数量 (默认20)
- `targetType`: 目标类型 (post/comment)

#### 8.2 批量点赞操作
```http
POST /api/likes/batch
Authorization: Bearer {token}
Content-Type: application/json

{
    "targets": [
        {
            "targetType": "post",
            "targetId": 1
        },
        {
            "targetType": "comment",
            "targetId": 2
        }
    ]
}
```

### 9. 话题关注管理模块

#### 9.1 获取用户关注的话题
```http
GET /api/user/topics/following?page=1&size=20
Authorization: Bearer {token}
```

#### 9.2 获取话题的关注者
```http
GET /api/topics/{topicId}/followers?page=1&size=20
```

### 10. 系统配置管理模块

#### 10.1 获取系统配置
```http
GET /api/config?keys=site_name,site_description
Authorization: Bearer {token}
```

#### 10.2 更新系统配置
```http
PUT /api/config
Authorization: Bearer {token}
Content-Type: application/json

{
    "configs": [
        {
            "configKey": "site_name",
            "configValue": "论坛名称",
            "configType": "string"
        },
        {
            "configKey": "max_post_length",
            "configValue": "5000",
            "configType": "number"
        }
    ]
}
```

### 11. 用户行为分析模块

#### 11.1 记录用户行为
```http
POST /api/behavior/log
Authorization: Bearer {token}
Content-Type: application/json

{
    "actionType": "view",
    "targetType": "post",
    "targetId": 1,
    "ipAddress": "192.168.1.1",
    "userAgent": "Mozilla/5.0..."
}
```

#### 11.2 获取用户行为统计
```http
GET /api/behavior/stats?userId=1&actionType=view&days=7
Authorization: Bearer {token}
```

#### 11.3 获取热门内容分析
```http
GET /api/behavior/analytics?type=popular&period=week
Authorization: Bearer {token}
```

### 12. 文件上传模块

#### 12.1 上传文件
```http
POST /api/upload
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: [文件]
type: image|video|audio|document
```

#### 12.2 获取文件信息
```http
GET /api/files/{fileId}
Authorization: Bearer {token}
```

#### 12.3 删除文件
```http
DELETE /api/files/{fileId}
Authorization: Bearer {token}
```

## 扩展功能建议

### 1. AI 智能功能
- **AI 内容生成**: 根据用户输入生成故事开头、日签内容
- **智能推荐**: 基于用户行为推荐相关帖子和话题
- **内容审核**: AI 自动检测不当内容

### 2. 社交互动增强
- **话题挑战**: 定期发布话题挑战活动
- **用户等级系统**: 根据活跃度设置用户等级
- **积分商城**: 用户积分兑换虚拟物品
- **每日签到**: 签到获得积分和徽章

### 3. 内容创作工具
- **富文本编辑器**: 支持图片、视频、表情插入
- **模板系统**: 提供日签、故事模板
- **草稿箱**: 保存未完成的帖子
- **定时发布**: 设置帖子发布时间

### 4. 数据分析功能
- **用户行为分析**: 统计用户活跃度、偏好
- **内容热度分析**: 分析热门话题和内容趋势
- **地域分析**: 基于地理位置的用户分布
- **时间分析**: 用户活跃时间段统计

### 5. 通知系统
- **实时通知**: WebSocket 推送新消息
- **邮件通知**: 重要事件邮件提醒
- **移动端推送**: 支持移动端消息推送
- **通知设置**: 用户自定义通知偏好

### 6. 安全与隐私
- **内容加密**: 敏感内容端到端加密
- **匿名模式**: 完全匿名发布选项
- **举报系统**: 用户举报不当内容
- **隐私设置**: 细粒度隐私控制

## 错误码定义

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 403 | 禁止访问 |
| 404 | 资源不存在 |
| 409 | 资源冲突 |
| 429 | 请求过于频繁 |
| 500 | 服务器内部错误 |

## 接口限流规则

- **用户注册/登录**: 5次/分钟
- **帖子发布**: 10次/小时
- **评论发布**: 30次/小时
- **搜索请求**: 100次/分钟
- **文件上传**: 20次/小时

## 缓存策略

- **热门帖子**: Redis缓存30分钟
- **用户信息**: Redis缓存1小时
- **话题列表**: Redis缓存2小时
- **搜索结果**: Redis缓存10分钟

## 消息队列使用场景

- **AI内容生成**: 异步处理，避免长时间阻塞
- **邮件发送**: 批量发送，提高效率
- **数据统计**: 异步计算用户行为数据
- **内容审核**: 异步审核用户发布的内容

---

*本文档基于您的需求设计，包含了完整的API接口定义和扩展功能建议。您可以根据实际开发进度逐步实现这些功能。*
