-- 整合数据库初始化脚本
-- 包含所有表的初始数据

-- 用户表数据
INSERT INTO sticknew.users (id, username, password, email, avatar, daily_bookmark, homepage_background, account_days, personal_signature, status, deleted_at, created_at, updated_at, nickname) VALUES (1, 'zhh', '123456', '928198963@qq.com', 'https://zhhhandsome.oss-cn-beijing.aliyuncs.com/image/2026/01/26/cd29c9aacdec438d96fce3c486122217.png', null, 'https://zhhhandsome.oss-cn-beijing.aliyuncs.com/image/2026/01/26/51fac7fe174b4d42afc58f39e8a52b89.png', 1, null, 'active', '2025-11-26 22:28:20', '2025-11-25 20:50:07', '2026-01-26 14:58:12', 'zhanghh');
INSERT INTO sticknew.users (id, username, password, email, avatar, daily_bookmark, homepage_background, account_days, personal_signature, status, deleted_at, created_at, updated_at, nickname) VALUES (2, 'zhh111', 'zhang2', null, null, null, null, 0, null, 'active', null, '2026-01-18 15:17:33', '2026-01-18 15:17:33', null);
INSERT INTO sticknew.users (id, username, password, email, avatar, daily_bookmark, homepage_background, account_days, personal_signature, status, deleted_at, created_at, updated_at, nickname) VALUES (3, '张艳华', '123456', null, null, null, null, 0, null, 'active', null, '2026-01-22 10:15:30', '2026-01-22 10:15:30', null);

-- 格言表数据
INSERT INTO sticknew.sayings (saying) VALUES ('厚德载物');
INSERT INTO sticknew.sayings (saying) VALUES ('天行健，君子以自强不息');
INSERT INTO sticknew.sayings (saying) VALUES ('学而不思则罔，思而不学则殆');
INSERT INTO sticknew.sayings (saying) VALUES ('知之为知之，不知为不知');

-- 帖子表数据
INSERT INTO sticknew.sticks (id, user_id, content, created_at, updated_at) VALUES (1, 1, '团队协作很重要', '2026-01-21 23:05:32.000000', '2026-01-21 23:05:32.000000');
INSERT INTO sticknew.sticks (id, user_id, content, created_at, updated_at) VALUES (2, 1, '持续学习是进步的关键', '2026-01-21 23:05:32.000000', '2026-01-21 23:05:32.000000');
INSERT INTO sticknew.sticks (id, user_id, content, created_at, updated_at) VALUES (3, 2, '保持好奇心', '2026-01-21 23:05:32.000000', '2026-01-21 23:05:32.000000');
INSERT INTO sticknew.sticks (id, user_id, content, created_at, updated_at) VALUES (4, 1, '呜呜呜呜呜呜呜', null, null);
INSERT INTO sticknew.sticks (id, user_id, content, created_at, updated_at) VALUES (5, 1, '测试贴', null, null);
INSERT INTO sticknew.sticks (id, user_id, content, created_at, updated_at) VALUES (6, 1, '我相信我的帖子', '2026-01-21 23:18:47.000000', '2026-01-21 23:18:47.000000');
INSERT INTO sticknew.sticks (id, user_id, content, created_at, updated_at) VALUES (7, 1, '哈哈哈哈', '2026-01-21 23:24:16.000000', '2026-01-21 23:24:16.000000');
INSERT INTO sticknew.sticks (id, user_id, content, created_at, updated_at) VALUES (8, 3, '嘻嘻嘻嘻', '2026-01-22 10:23:08.000000', '2026-01-22 10:23:08.000000');
INSERT INTO sticknew.sticks (id, user_id, content, created_at, updated_at) VALUES (9, 3, '今天不小心被别人骂了，好难过', '2026-01-22 10:23:40.000000', '2026-01-22 10:23:40.000000');
INSERT INTO sticknew.sticks (id, user_id, content, created_at, updated_at) VALUES (10, 3, '我今天不小心创到了别人，他们看起来很生气，把我一顿骂。我觉得人生没有希望了', '2026-01-22 10:25:14.000000', '2026-01-22 10:25:14.000000');
INSERT INTO sticknew.sticks (id, user_id, content, created_at, updated_at) VALUES (11, 3, '张哲闻你就个大帅逼', '2026-01-22 10:26:30.000000', '2026-01-22 10:41:17.000000');
INSERT INTO sticknew.sticks (id, user_id, content, created_at, updated_at) VALUES (12, 1, '灯火葳蕤，揉皱你眼眉', '2026-01-26 15:00:31.000000', '2026-01-26 15:00:31.000000');

-- 个人便签表数据
INSERT INTO sticknew.psticks (id, user_id, name, content, created_at, updated_at, stick_id, spirits) VALUES (1, 1, '工作计划', '完成项目A的开发', '2026-01-21 23:05:32.000000', '2026-01-21 23:05:32.000000', null, null);
INSERT INTO sticknew.psticks (id, user_id, name, content, created_at, updated_at, stick_id, spirits) VALUES (2, 1, '学习目标', '掌握Spring Boot高级特性', '2026-01-21 23:05:32.000000', '2026-01-21 23:05:32.000000', null, null);
INSERT INTO sticknew.psticks (id, user_id, name, content, created_at, updated_at, stick_id, spirits) VALUES (3, 2, '生活记录', '周末去爬山', '2026-01-21 23:05:32.000000', '2026-01-21 23:05:32.000000', null, null);
INSERT INTO sticknew.psticks (id, user_id, name, content, created_at, updated_at, stick_id, spirits) VALUES (4, 3, '张哲闻', '你就个大帅逼', '2026-01-22 00:00:00.000000', null, 11, 5);

-- 邮件表数据
INSERT INTO sticknew.mail (id, sender_id, stamp_type, stamp_content, sender_nickname, recipient_email, content, status, read_at, created_at) VALUES (1, 1, '夏日', '开心捏', 'zhh', '软件内人员', '这是内容', 'sent', null, '2025-12-24 11:06:35');
INSERT INTO sticknew.mail (id, sender_id, stamp_type, stamp_content, sender_nickname, recipient_email, content, status, read_at, created_at) VALUES (2, 1, 'love', '1111', '11111', '软件内人员', '1111111212121', 'sent', null, '2026-01-27 09:54:07');
INSERT INTO sticknew.mail (id, sender_id, stamp_type, stamp_content, sender_nickname, recipient_email, content, status, read_at, created_at) VALUES (3, 1, 'love', 'sdfgsdgd', 'sffsdf', '软件内人员', 'afgsdgf', 'sent', null, '2026-01-27 18:36:23');

-- 已收邮件表数据
INSERT INTO sticknew.received_mail (id, recipient_id, sender_id, content, stamp_type, sender_nickname, original_mail_id, status, read_at, created_at) VALUES (539365927221723137, 1, 1, '这是内容', '夏日', 'zhh', 1, 'read', null, '2025-12-24 11:35:16');

-- 邮件评论表数据
INSERT INTO sticknew.mail_comment (id, mail_id, commenter_id, content, created_at) VALUES (4, 1, 1, '这是内容', '2025-12-24 11:35:16');

-- 帖子表数据 (posts表)
INSERT INTO sticknew.posts (id, user_id, topic_id, title, content, post_type, status, like_count, collect_count, comment_count, share_count, view_count, publish_location, latitude, longitude, deleted_at, created_at, updated_at) VALUES (1, 1, 1, '这是轻贴的第一个帖子', '明天面试，好紧张', 'story', 'published', 0, 0, 0, 0, 0, null, null, null, null, '2025-12-24 12:03:53', '2025-12-24 12:03:53');
INSERT INTO sticknew.posts (id, user_id, topic_id, title, content, post_type, status, like_count, collect_count, comment_count, share_count, view_count, publish_location, latitude, longitude, deleted_at, created_at, updated_at) VALUES (2, 1, 1, '这是轻贴的第一个帖子', '明天面试，好紧张', 'story', 'published', 0, 0, 0, 0, 0, null, null, null, null, '2025-12-24 12:06:54', '2025-12-24 12:06:54');
INSERT INTO sticknew.posts (id, user_id, topic_id, title, content, post_type, status, like_count, collect_count, comment_count, share_count, view_count, publish_location, latitude, longitude, deleted_at, created_at, updated_at) VALUES (4, 1, null, '1去1', '11111', 'story', 'published', 1, 0, 0, 0, 0, null, null, null, null, '2026-01-19 00:57:48', '2026-01-26 12:00:00');
INSERT INTO sticknew.posts (id, user_id, topic_id, title, content, post_type, status, like_count, collect_count, comment_count, share_count, view_count, publish_location, latitude, longitude, deleted_at, created_at, updated_at) VALUES (5, 1, null, '111', '131323232', 'story', 'published', 1, 0, 0, 0, 0, null, null, null, null, '2026-01-19 22:01:13', '2026-01-26 12:00:00');
INSERT INTO sticknew.posts (id, user_id, topic_id, title, content, post_type, status, like_count, collect_count, comment_count, share_count, view_count, publish_location, latitude, longitude, deleted_at, created_at, updated_at) VALUES (10, 1, 123, '最美的标题', '安阳真是一个差劲的地方啊。我太不喜欢这个地方了，啊啊啊啊', 'story', 'published', 1, 0, 5, 8, 0, '安阳市文峰区', 39.90420000, 116.40740000, null, '2026-01-21 22:05:03', '2026-01-27 18:35:14');
INSERT INTO sticknew.posts (id, user_id, topic_id, title, content, post_type, status, like_count, collect_count, comment_count, share_count, view_count, publish_location, latitude, longitude, deleted_at, created_at, updated_at) VALUES (11, 1, null, '11', '1111', 'story', 'published', 1, 0, 0, 0, 0, null, null, null, null, '2026-01-26 10:26:09', '2026-01-27 10:00:00');
INSERT INTO sticknew.posts (id, user_id, topic_id, title, content, post_type, status, like_count, collect_count, comment_count, share_count, view_count, publish_location, latitude, longitude, deleted_at, created_at, updated_at) VALUES (12, 1, null, '11', '1111', 'story', 'published', 1, 0, 0, 3, 0, null, null, null, null, '2026-01-27 09:28:18', '2026-01-27 16:54:42');
INSERT INTO sticknew.posts (id, user_id, topic_id, title, content, post_type, status, like_count, collect_count, comment_count, share_count, view_count, publish_location, latitude, longitude, deleted_at, created_at, updated_at) VALUES (13, 1, 5, '赵舒阳是大煞笔', '1123132423435435', 'story', 'published', 0, 0, 0, 0, 0, null, null, null, null, '2026-01-27 18:17:36', '2026-01-27 18:17:36');
INSERT INTO sticknew.posts (id, user_id, topic_id, title, content, post_type, status, like_count, collect_count, comment_count, share_count, view_count, publish_location, latitude, longitude, deleted_at, created_at, updated_at) VALUES (14, 1, 5, '赵舒阳是个好几把', '是的，就是个几把', 'story', 'published', 0, 0, 0, 0, 0, null, null, null, null, '2026-01-27 18:23:36', '2026-01-27 18:23:36');

-- 帖子评论表数据
INSERT INTO sticknew.post_comment (id, post_id, commenter_id, content, created_at, root_id, parent_id, likes, dislikes, reply_count) VALUES (551591560219721729, 10, 1, '这个男的真好看', '2026-01-26 10:16:59', 551591560219721729, 0, 0, 0, 0);
INSERT INTO sticknew.post_comment (id, post_id, commenter_id, content, created_at, root_id, parent_id, likes, dislikes, reply_count) VALUES (551949352470315009, 10, 1, '哈哈哈哈哈哈，我们的爱情到这刚刚好，不争也不吵', '2026-01-27 09:25:24', 551949352470315009, 0, 0, 0, 3);
INSERT INTO sticknew.post_comment (id, post_id, commenter_id, content, created_at, root_id, parent_id, likes, dislikes, reply_count) VALUES (552052285656530946, 10, 1, '哈哈哈哈哈', '2026-01-27 16:04:50', 551949352470315009, 551949352470315009, 0, 0, 0);
INSERT INTO sticknew.post_comment (id, post_id, commenter_id, content, created_at, root_id, parent_id, likes, dislikes, reply_count) VALUES (552064934335217667, 10, 1, '你笑你妈呢', '2026-01-27 16:53:55', 551949352470315009, 552052285656530946, 0, 0, 0);
INSERT INTO sticknew.post_comment (id, post_id, commenter_id, content, created_at, root_id, parent_id, likes, dislikes, reply_count) VALUES (552065041709400068, 10, 1, '你笑你妈呢', '2026-01-27 16:54:20', 551949352470315009, 552064934335217667, 0, 0, 0);

-- 附件表数据
INSERT INTO sticknew.attachment (id, file_id, file_url, file_name, file_size, file_type, mime_type, business_type, business_id, uploader_id, upload_time, created_at, updated_at) VALUES (548849867846320129, 'png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAAJCAYAAADzRkbkAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAAsSURBVBhXLcmxDQAgDASxy332342WFVgBKTS4NWufMQmqWAO2QQ12+ePW8AC/bAbOJdp4KQAAAABJRU5ErkJggg==', 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAAJCAYAAADzRkbkAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAAsSURBVBhXLcmxDQAgDASxy332342WFVgBKTS4NWufMQmqWAO2QQ12+ePW8AC/bAbOJdp4KQAAAABJRU5ErkJggg==', '屏幕截图 2025-09-18 153959.png', null, 'image', 'image/png', 'post', 4, 1, '2026-01-19 00:57:48', '2026-01-19 00:57:48', '2026-01-19 00:57:48');
INSERT INTO sticknew.attachment (id, file_id, file_url, file_name, file_size, file_type, mime_type, business_type, business_id, uploader_id, upload_time, created_at, updated_at) VALUES (550182652032843777, 'image/2026/01/22/860c4b67271e4eba87a489bc4d4b65f1.jpg', 'https://zhhhandsome.oss-cn-beijing.aliyuncs.com/image/2026/01/22/860c4b67271e4eba87a489bc4d4b65f1.jpg', '1.jpg', 221557, 'image', 'image/jpeg', 'post', 10, 1, '2026-01-22 15:09:42', '2026-01-22 15:09:42', '2026-01-22 15:09:42');
INSERT INTO sticknew.attachment (id, file_id, file_url, file_name, file_size, file_type, mime_type, business_type, business_id, uploader_id, upload_time, created_at, updated_at) VALUES (550195721618325506, 'image/2026/01/22/0b014d703ec3419aa951b2101d654c9a.jpg', 'https://zhhhandsome.oss-cn-beijing.aliyuncs.com/image/2026/01/22/0b014d703ec3419aa951b2101d654c9a.jpg', '1.jpg', 221557, 'image', 'image/jpeg', 'post', 10, 1, '2026-01-22 16:00:24', '2026-01-22 16:00:24', '2026-01-22 16:00:24');
INSERT INTO sticknew.attachment (id, file_id, file_url, file_name, file_size, file_type, mime_type, business_type, business_id, uploader_id, upload_time, created_at, updated_at) VALUES (551662152302198787, 'image/2026/01/26/cd29c9aacdec438d96fce3c486122217.png', 'https://zhhhandsome.oss-cn-beijing.aliyuncs.com/image/2026/01/26/cd29c9aacdec438d96fce3c486122217.png', '用户更新文件', null, 'image', 'image/*', 'user', 1, 1, '2026-01-26 14:50:55', '2026-01-26 14:50:55', '2026-01-26 14:50:55');
INSERT INTO sticknew.attachment (id, file_id, file_url, file_name, file_size, file_type, mime_type, business_type, business_id, uploader_id, upload_time, created_at, updated_at) VALUES (551664033497874436, 'image/2026/01/26/51fac7fe174b4d42afc58f39e8a52b89.png', 'https://zhhhandsome.oss-cn-beijing.aliyuncs.com/image/2026/01/26/51fac7fe174b4d42afc58f39e8a52b89.png', null, null, 'image', 'application/octet-stream', 'user', 1, 1, '2026-01-26 14:58:13', '2026-01-26 14:58:13', '2026-01-26 14:58:13');
INSERT INTO sticknew.attachment (id, file_id, file_url, file_name, file_size, file_type, mime_type, business_type, business_id, uploader_id, upload_time, created_at, updated_at) VALUES (551950104089591809, 'image/2026/01/27/9d68f8a6de6b43e2a0362c45054d0079.png', 'https://zhhhandsome.oss-cn-beijing.aliyuncs.com/image/2026/01/27/9d68f8a6de6b43e2a0362c45054d0079.png', '屏幕截图 2025-06-25 140825.png', null, 'image', 'image/png', 'post', 12, 1, '2026-01-27 09:28:18', '2026-01-27 09:28:18', '2026-01-27 09:28:18');
INSERT INTO sticknew.attachment (id, file_id, file_url, file_name, file_size, file_type, mime_type, business_type, business_id, uploader_id, upload_time, created_at, updated_at) VALUES (552086499366010882, 'image/2026/01/27/bbfd1ec563234a7db7e65b7bdd21f21a.png', 'https://zhhhandsome.oss-cn-beijing.aliyuncs.com/image/2026/01/27/bbfd1ec563234a7db7e65b7bdd21f21a.png', '屏幕截图 2025-06-25 140825.png', null, 'image', 'image/png', 'post', 13, 1, '2026-01-27 18:17:36', '2026-01-27 18:17:36', '2026-01-27 18:17:36');
INSERT INTO sticknew.attachment (id, file_id, file_url, file_name, file_size, file_type, mime_type, business_type, business_id, uploader_id, upload_time, created_at, updated_at) VALUES (552088049849204739, 'image/2026/01/27/61199ba367994d55aa9077c8041d049c.png', 'https://zhhhandsome.oss-cn-beijing.aliyuncs.com/image/2026/01/27/61199ba367994d55aa9077c8041d049c.png', '屏幕截图 2026-01-04 163808.png', null, 'image', 'image/png', 'post', 14, 1, '2026-01-27 18:23:36', '2026-01-27 18:23:36', '2026-01-27 18:23:36');

-- 话题表数据
INSERT INTO sticknew.topic (id, name, description, cover_image, post_count, follower_count, status, user_id, created_at, updated_at) VALUES (1, '冬日狂想曲', '冬天虽然很冷，但是也很热', 'sogasinei', 3, 1, 'pending', 1, '2025-12-23 23:12:51', '2026-01-27 09:55:09');
INSERT INTO sticknew.topic (id, name, description, cover_image, post_count, follower_count, status, user_id, created_at, updated_at) VALUES (2, '冬日狂想曲', '冬天虽然很冷，但是也很热', 'sogasinei', 0, 0, 'pending', 1, '2025-12-23 23:13:29', '2025-12-23 23:13:29');
INSERT INTO sticknew.topic (id, name, description, cover_image, post_count, follower_count, status, user_id, created_at, updated_at) VALUES (3, '冬日狂想曲', '冬天虽然很冷，但是也很热', 'sogasinei', 0, 0, 'pending', 1, '2025-12-23 23:22:32', '2025-12-23 23:22:32');
INSERT INTO sticknew.topic (id, name, description, cover_image, post_count, follower_count, status, user_id, created_at, updated_at) VALUES (4, '冬日狂想曲', '冬天虽然很冷，但是也很热', 'sogasinei', 0, 0, 'pending', 1, '2025-12-23 23:23:46', '2025-12-23 23:23:46');
INSERT INTO sticknew.topic (id, name, description, cover_image, post_count, follower_count, status, user_id, created_at, updated_at) VALUES (5, '冬日狂想曲', '冬天虽然很冷，但是也很热', 'sogasinei', 2, 1, 'pending', 1, '2025-12-23 23:25:39', '2026-01-27 18:23:36');
INSERT INTO sticknew.topic (id, name, description, cover_image, post_count, follower_count, status, user_id, created_at, updated_at) VALUES (8, '23523453463', '1312342345', null, 0, 0, 'pending', 1, '2026-01-27 18:26:48', '2026-01-27 18:26:48');

-- 话题关注表数据
INSERT INTO sticknew.topic_follow (id, user_id, topic_id, created_at) VALUES (22, 1, 1, '2026-01-27 09:55:09');
INSERT INTO sticknew.topic_follow (id, user_id, topic_id, created_at) VALUES (23, 1, 5, '2026-01-27 16:11:36');

-- 点赞表数据
INSERT INTO sticknew.likes (id, user_id, target_type, target_id, created_at) VALUES (7, 3, 'post', 4, '2026-01-24 22:31:24');
INSERT INTO sticknew.likes (id, user_id, target_type, target_id, created_at) VALUES (10, 3, 'post', 5, '2026-01-24 22:54:53');
INSERT INTO sticknew.likes (id, user_id, target_type, target_id, created_at) VALUES (21, 1, 'post', 11, '2026-01-26 16:30:43');
INSERT INTO sticknew.likes (id, user_id, target_type, target_id, created_at) VALUES (23, 1, 'post', 12, '2026-01-27 16:56:13');
INSERT INTO sticknew.likes (id, user_id, target_type, target_id, created_at) VALUES (24, 1, 'post', 14, '2026-01-27 18:26:13');
INSERT INTO sticknew.likes (id, user_id, target_type, target_id, created_at) VALUES (25, 1, 'post', 10, '2026-01-27 18:34:51');

-- 收藏表数据
INSERT INTO sticknew.favorite (id, user_id, post_id, created_at) VALUES (2, 1, 10, '2026-01-21 22:19:24');