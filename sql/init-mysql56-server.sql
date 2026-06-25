-- =============================================================================
-- CloudDisk Pro — MySQL 5.6.x 服务器空库初始化脚本
-- =============================================================================
-- 适用版本 : MySQL 5.6.51（及同系列 5.6.x）
-- 执行前提 : 空库 / 新服务器首次部署，请使用 root 或具备建库建用户权限的账号执行
-- 应用配置 : application-prod.yml 默认连接
--              库名 cloud_disk / 用户 cloud_disk / 密码 root
--              Redis 密码 root（非本脚本范围）
--
-- 与 sql/init.sql（MySQL 8）差异说明：
--   1. TEXT 列去掉 DEFAULT NULL（5.6 不允许 TEXT/BLOB 默认值）
--   2. 长 VARCHAR 索引改为前缀索引，避免 utf8mb4 下 767 字节索引长度限制
--   3. 时间列使用 DATETIME + CURRENT_TIMESTAMP（5.6.5+ 支持 DATETIME 默认值）
--   5. xxl_job_registry 联合唯一索引使用前缀长度（5.6 utf8mb4 索引上限 767 字节）
--
-- 已有库升级：执行 sql/update-mysql56-quota-3g-5g.sql（默认配额 3GB/5GB）
--
-- 执行示例：
--   mysql -uroot -p < sql/init-mysql56-server.sql
-- =============================================================================

/*!40101 SET @OLD_CHARACTER_SET_CLIENT = @@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS = @@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION = @@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40101 SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS = 0 */;
/*!40101 SET @OLD_SQL_MODE = @@SQL_MODE, SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO' */;

-- =============================================================================
-- 业务库：cloud_disk
-- =============================================================================
CREATE DATABASE IF NOT EXISTS `cloud_disk`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `cloud_disk`;

-- -----------------------------------------------------------------------------
-- 业务账号（与 prod 配置一致：cloud_disk / root）
-- MySQL 5.6 无 CREATE USER IF NOT EXISTS；用 GRANT ... IDENTIFIED BY 可重复执行：
--   用户不存在 → 自动创建；已存在 → 更新密码并授权（避免 ERROR 1396）
-- -----------------------------------------------------------------------------
GRANT ALL PRIVILEGES ON `cloud_disk`.* TO 'cloud_disk'@'localhost' IDENTIFIED BY 'root';
GRANT ALL PRIVILEGES ON `cloud_disk`.* TO 'cloud_disk'@'%' IDENTIFIED BY 'root';
FLUSH PRIVILEGES;

-- 用户表
CREATE TABLE IF NOT EXISTS `tb_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(64) NOT NULL,
  `password` VARCHAR(120) NOT NULL COMMENT 'BCrypt',
  `nickname` VARCHAR(64) DEFAULT NULL,
  `avatar` VARCHAR(512) DEFAULT NULL,
  `email` VARCHAR(128) DEFAULT NULL,
  `phone` VARCHAR(32) DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0禁用 2待审核',
  `role` VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN/SUPER_ADMIN',
  `storage_quota` BIGINT NOT NULL DEFAULT 0 COMMENT '存储配额(字节)，0=不限；USER默认3GB，ADMIN默认5GB',
  `storage_used` BIGINT NOT NULL DEFAULT 0 COMMENT '已用存储(字节)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 默认超级管理员由应用首次启动时创建（admin / admin123，role=SUPER_ADMIN，配额不限）

-- 文件夹表
CREATE TABLE IF NOT EXISTS `tb_folder` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '0=根目录',
  `folder_name` VARCHAR(255) NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1回收站',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_parent` (`user_id`, `parent_id`, `deleted`),
  KEY `idx_user_deleted_time` (`user_id`, `deleted`, `update_time`),
  KEY `idx_user_name` (`user_id`, `folder_name`(191)),
  CONSTRAINT `fk_folder_user` FOREIGN KEY (`user_id`) REFERENCES `tb_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 文件表
CREATE TABLE IF NOT EXISTS `tb_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `folder_id` BIGINT NOT NULL DEFAULT 0 COMMENT '0=根目录',
  `file_name` VARCHAR(512) NOT NULL,
  `file_size` BIGINT NOT NULL DEFAULT 0,
  `file_type` VARCHAR(128) DEFAULT NULL COMMENT 'MIME',
  `file_md5` VARCHAR(64) DEFAULT NULL,
  `storage_path` VARCHAR(1024) NOT NULL,
  `bucket_name` VARCHAR(64) DEFAULT 'local',
  `thumbnail_path` VARCHAR(1024) DEFAULT NULL,
  `poster_path` VARCHAR(1024) DEFAULT NULL COMMENT '视频封面',
  `transcode_path` VARCHAR(1024) DEFAULT NULL COMMENT '转码后视频',
  `transcode_status` VARCHAR(32) DEFAULT 'NONE' COMMENT 'NONE/PENDING/PROCESSING/DONE/FAILED',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0回收站',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_folder` (`user_id`, `folder_id`, `status`),
  KEY `idx_md5_status` (`file_md5`, `status`),
  KEY `idx_user_status_time` (`user_id`, `status`, `update_time`),
  KEY `idx_file_md5` (`file_md5`),
  KEY `idx_file_name` (`file_name`(191)),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_transcode_status` (`transcode_status`),
  CONSTRAINT `fk_file_user` FOREIGN KEY (`user_id`) REFERENCES `tb_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 分享表
CREATE TABLE IF NOT EXISTS `tb_share` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `file_id` BIGINT DEFAULT NULL,
  `share_type` VARCHAR(16) NOT NULL DEFAULT 'FILE' COMMENT 'FILE/FOLDER',
  `folder_id` BIGINT DEFAULT NULL COMMENT '文件夹分享',
  `share_code` VARCHAR(32) NOT NULL COMMENT '分享短码',
  `extract_code` VARCHAR(16) DEFAULT NULL COMMENT '提取码，空=公开',
  `expire_time` DATETIME DEFAULT NULL COMMENT 'NULL=永久',
  `view_count` INT NOT NULL DEFAULT 0,
  `download_count` INT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1有效 0失效',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_share_code` (`share_code`),
  KEY `idx_share_file` (`file_id`),
  KEY `idx_share_folder` (`folder_id`),
  KEY `idx_share_user` (`user_id`),
  KEY `idx_share_status_expire` (`status`, `expire_time`),
  CONSTRAINT `fk_share_user` FOREIGN KEY (`user_id`) REFERENCES `tb_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_share_file` FOREIGN KEY (`file_id`) REFERENCES `tb_file` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 分片上传会话
CREATE TABLE IF NOT EXISTS `tb_upload_session` (
  `id` VARCHAR(64) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `file_name` VARCHAR(512) NOT NULL,
  `file_md5` VARCHAR(64) DEFAULT NULL,
  `folder_id` BIGINT NOT NULL DEFAULT 0,
  `total_size` BIGINT NOT NULL,
  `chunk_size` INT NOT NULL,
  `total_chunks` INT NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_upload_user` (`user_id`),
  KEY `idx_upload_md5` (`file_md5`),
  KEY `idx_upload_expires` (`expires_at`),
  CONSTRAINT `fk_upload_user` FOREIGN KEY (`user_id`) REFERENCES `tb_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 已上传分片记录
CREATE TABLE IF NOT EXISTS `tb_file_chunk` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `upload_id` VARCHAR(64) NOT NULL,
  `chunk_no` INT NOT NULL,
  `chunk_size` INT NOT NULL,
  `upload_status` TINYINT NOT NULL DEFAULT 1 COMMENT '1已上传',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_upload_chunk` (`upload_id`, `chunk_no`),
  KEY `idx_chunk_upload` (`upload_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 审计日志
CREATE TABLE IF NOT EXISTS `tb_audit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT DEFAULT NULL,
  `username` VARCHAR(64) DEFAULT NULL,
  `action` VARCHAR(64) NOT NULL,
  `target_type` VARCHAR(32) DEFAULT NULL,
  `target_id` VARCHAR(64) DEFAULT NULL,
  `detail` VARCHAR(512) DEFAULT NULL,
  `ip` VARCHAR(64) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_user` (`user_id`),
  KEY `idx_audit_action` (`action`),
  KEY `idx_audit_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 团队空间
CREATE TABLE IF NOT EXISTS `tb_team_space` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(128) NOT NULL,
  `owner_id` BIGINT NOT NULL,
  `root_folder_id` BIGINT NOT NULL COMMENT '团队根文件夹',
  `max_size` BIGINT NOT NULL DEFAULT 0 COMMENT '0=不限',
  `avatar` VARCHAR(512) DEFAULT NULL COMMENT '团队头像路径',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_team_owner` (`owner_id`),
  KEY `idx_team_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 团队成员
CREATE TABLE IF NOT EXISTS `tb_team_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `space_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `role` VARCHAR(16) NOT NULL DEFAULT 'MEMBER' COMMENT 'OWNER/ADMIN/MEMBER/VIEWER',
  `join_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_space_user` (`space_id`, `user_id`),
  KEY `idx_member_user` (`user_id`),
  CONSTRAINT `fk_member_space` FOREIGN KEY (`space_id`) REFERENCES `tb_team_space` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_member_user` FOREIGN KEY (`user_id`) REFERENCES `tb_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 团队邀请
CREATE TABLE IF NOT EXISTS `tb_team_invitation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `space_id` BIGINT NOT NULL,
  `inviter_id` BIGINT NOT NULL,
  `invitee_id` BIGINT NOT NULL,
  `role` VARCHAR(16) NOT NULL DEFAULT 'MEMBER' COMMENT 'OWNER/ADMIN/MEMBER/VIEWER',
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_invitee_status` (`invitee_id`, `status`),
  KEY `idx_space_invitee` (`space_id`, `invitee_id`),
  CONSTRAINT `fk_inv_space` FOREIGN KEY (`space_id`) REFERENCES `tb_team_space` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_inv_inviter` FOREIGN KEY (`inviter_id`) REFERENCES `tb_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_inv_invitee` FOREIGN KEY (`invitee_id`) REFERENCES `tb_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 通知（MySQL 5.6：TEXT 列不能写 DEFAULT）
CREATE TABLE IF NOT EXISTS `tb_notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(32) NOT NULL COMMENT 'TRANSCODE_DONE/SHARE_EXPIRED/TEAM_INVITED',
  `title` VARCHAR(256) NOT NULL,
  `content` TEXT,
  `ref_id` VARCHAR(64) DEFAULT NULL COMMENT '关联资源ID',
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_notif_user` (`user_id`, `is_read`),
  KEY `idx_notif_user_time` (`user_id`, `create_time`),
  KEY `idx_notif_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 容量扩容申请
CREATE TABLE IF NOT EXISTS `tb_quota_application` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `current_quota` BIGINT NOT NULL,
  `apply_quota` BIGINT NOT NULL COMMENT '申请增加到的总配额(字节)',
  `reason` VARCHAR(512) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  `approval_opinion` VARCHAR(512) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`, `status`),
  CONSTRAINT `fk_quota_user` FOREIGN KEY (`user_id`) REFERENCES `tb_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- 调度库：xxl_job（可选，启用 XXL-JOB 时需要）
-- =============================================================================
CREATE DATABASE IF NOT EXISTS `xxl_job`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON `xxl_job`.* TO 'cloud_disk'@'localhost';
GRANT ALL PRIVILEGES ON `xxl_job`.* TO 'cloud_disk'@'%';

USE `xxl_job`;

CREATE TABLE IF NOT EXISTS `xxl_job_group` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `app_name` VARCHAR(64) NOT NULL,
  `title` VARCHAR(64) NOT NULL,
  `address_type` TINYINT NOT NULL DEFAULT 0,
  `address_list` TEXT,
  `update_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_registry` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `registry_group` VARCHAR(50) NOT NULL,
  `registry_key` VARCHAR(255) NOT NULL,
  `registry_value` VARCHAR(255) NOT NULL,
  `update_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_g_k_v` (`registry_group`, `registry_key`(100), `registry_value`(41))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_info` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `job_group` INT NOT NULL,
  `job_desc` VARCHAR(255) NOT NULL,
  `add_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `author` VARCHAR(64) DEFAULT NULL,
  `alarm_email` VARCHAR(255) DEFAULT NULL,
  `schedule_type` VARCHAR(50) NOT NULL DEFAULT 'NONE',
  `schedule_conf` VARCHAR(128) DEFAULT NULL,
  `misfire_strategy` VARCHAR(50) NOT NULL DEFAULT 'DO_NOTHING',
  `executor_route_strategy` VARCHAR(50) DEFAULT NULL,
  `executor_handler` VARCHAR(255) DEFAULT NULL,
  `executor_param` TEXT,
  `executor_block_strategy` VARCHAR(50) DEFAULT NULL,
  `executor_timeout` INT NOT NULL DEFAULT 0,
  `executor_fail_retry_count` INT NOT NULL DEFAULT 0,
  `glue_type` VARCHAR(50) NOT NULL,
  `glue_source` MEDIUMTEXT,
  `glue_remark` VARCHAR(128) DEFAULT NULL,
  `glue_updatetime` DATETIME DEFAULT NULL,
  `child_jobid` VARCHAR(255) DEFAULT NULL,
  `trigger_status` TINYINT NOT NULL DEFAULT 0,
  `trigger_last_time` BIGINT NOT NULL DEFAULT 0,
  `trigger_next_time` BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `job_group` INT NOT NULL,
  `job_id` INT NOT NULL,
  `executor_address` VARCHAR(255) DEFAULT NULL,
  `executor_handler` VARCHAR(255) DEFAULT NULL,
  `executor_param` TEXT,
  `executor_sharding_param` VARCHAR(20) DEFAULT NULL,
  `executor_fail_retry_count` INT NOT NULL DEFAULT 0,
  `trigger_time` DATETIME DEFAULT NULL,
  `trigger_code` INT NOT NULL,
  `trigger_msg` TEXT,
  `handle_time` DATETIME DEFAULT NULL,
  `handle_code` INT NOT NULL,
  `handle_msg` TEXT,
  `alarm_status` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `I_trigger_time` (`trigger_time`),
  KEY `I_handle_code` (`handle_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_log_report` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `trigger_day` DATETIME DEFAULT NULL,
  `running_count` INT NOT NULL DEFAULT 0,
  `suc_count` INT NOT NULL DEFAULT 0,
  `fail_count` INT NOT NULL DEFAULT 0,
  `update_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_trigger_day` (`trigger_day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_logglue` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `job_id` INT NOT NULL,
  `glue_type` VARCHAR(50) DEFAULT NULL,
  `glue_source` MEDIUMTEXT,
  `glue_remark` VARCHAR(128) NOT NULL,
  `add_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_lock` (
  `lock_name` VARCHAR(50) NOT NULL,
  PRIMARY KEY (`lock_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_user` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL,
  `password` VARCHAR(100) NOT NULL,
  `token` VARCHAR(100) DEFAULT NULL,
  `role` TINYINT NOT NULL,
  `permission` VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `xxl_job_user` (`id`, `username`, `password`, `role`, `permission`)
VALUES (1, 'admin', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 1, NULL);

INSERT IGNORE INTO `xxl_job_lock` (`lock_name`) VALUES ('schedule_lock');

INSERT IGNORE INTO `xxl_job_group` (`id`, `app_name`, `title`, `address_type`, `update_time`)
VALUES (1, 'clouddisk-pro', 'CloudDisk Pro 执行器', 0, NOW());

FLUSH PRIVILEGES;

/*!40101 SET SQL_MODE = @OLD_SQL_MODE */;
/*!40101 SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT = @OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS = @OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION = @OLD_COLLATION_CONNECTION */;
