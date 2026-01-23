/* 创建psticks表 */
CREATE TABLE `psticks` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '名称',
  `content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '内容',
  `created_at` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人便签表';

/* 插入psticks表初始数据 */
INSERT INTO `psticks` (`user_id`, `name`, `content`, `created_at`, `updated_at`) VALUES
(1, '工作计划', '完成项目A的开发', NOW(), NOW()),
(1, '学习目标', '掌握Spring Boot高级特性', NOW(), NOW()),
(2, '生活记录', '周末去爬山', NOW(), NOW());

/* 创建sayings表 */
CREATE TABLE `sayings` (
  `saying` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '格言内容',
  PRIMARY KEY (`saying`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='格言表';

/* 插入sayings表初始数据 */
INSERT INTO `sayings` (`saying`) VALUES
('天行健，君子以自强不息'),
('厚德载物'),
('知之为知之，不知为不知'),
('学而不思则罔，思而不学则殆');

/* 创建sticks表 */
CREATE TABLE `sticks` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '内容',
  `created_at` datetime(6) NULL DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共便签表';

/* 插入sticks表初始数据 */
INSERT INTO `sticks` (`user_id`, `content`, `created_at`, `updated_at`) VALUES
(1, '团队协作很重要', NOW(), NOW()),
(1, '持续学习是进步的关键', NOW(), NOW()),
(2, '保持好奇心', NOW(), NOW());