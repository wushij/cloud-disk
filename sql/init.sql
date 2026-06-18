-- CloudDisk Pro 完整数据库脚本（MySQL 8.x，utf8mb4）
-- 含 cloud_disk 业务库 + xxl_job 调度库

-- =============================================================================
-- cloud_disk 业务库
-- =============================================================================
CREATE DATABASE IF NOT EXISTS `cloud_disk`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `cloud_disk`;

-- 用户表
CREATE TABLE IF NOT EXISTS `tb_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(64) NOT NULL,
  `password` VARCHAR(120) NOT NULL COMMENT 'BCrypt',
  `nickname` VARCHAR(64) DEFAULT NULL,
  `avatar` VARCHAR(512) DEFAULT NULL,
  `email` VARCHAR(128) DEFAULT NULL,
  `phone` VARCHAR(32) DEFAULT NULL,
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  `role` VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT 'USER/ADMIN',
  `storage_quota` BIGINT NOT NULL DEFAULT 0 COMMENT '存储配额(字节)，0=不限',
  `storage_used` BIGINT NOT NULL DEFAULT 0 COMMENT '已用存储(字节)',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 文件夹表
CREATE TABLE IF NOT EXISTS `tb_folder` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '0=根目录',
  `folder_name` VARCHAR(255) NOT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1回收站',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_user_parent` (`user_id`, `parent_id`, `deleted`),
  KEY `idx_user_deleted_time` (`user_id`, `deleted`, `update_time`),
  KEY `idx_user_name` (`user_id`, `folder_name`),
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
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_user_folder` (`user_id`, `folder_id`, `status`),
  KEY `idx_md5_status` (`file_md5`, `status`),
  KEY `idx_user_status_time` (`user_id`, `status`, `update_time`),
  KEY `idx_file_md5` (`file_md5`),
  KEY `idx_file_name` (`file_name`),
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
  `expire_time` DATETIME(6) DEFAULT NULL COMMENT 'NULL=永久',
  `view_count` INT NOT NULL DEFAULT 0,
  `download_count` INT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1有效 0失效',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
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
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `expires_at` DATETIME(6) DEFAULT NULL,
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
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
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
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
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
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_team_owner` (`owner_id`),
  KEY `idx_team_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 团队成员
CREATE TABLE IF NOT EXISTS `tb_team_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `space_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `role` VARCHAR(16) NOT NULL DEFAULT 'MEMBER' COMMENT 'OWNER/ADMIN/MEMBER',
  `join_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_space_user` (`space_id`, `user_id`),
  KEY `idx_member_user` (`user_id`),
  CONSTRAINT `fk_member_space` FOREIGN KEY (`space_id`) REFERENCES `tb_team_space` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_member_user` FOREIGN KEY (`user_id`) REFERENCES `tb_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 通知
CREATE TABLE IF NOT EXISTS `tb_notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(32) NOT NULL COMMENT 'TRANSCODE_DONE/SHARE_EXPIRED/TEAM_INVITED',
  `title` VARCHAR(256) NOT NULL,
  `content` TEXT DEFAULT NULL,
  `ref_id` VARCHAR(64) DEFAULT NULL COMMENT '关联资源ID',
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_notif_user` (`user_id`, `is_read`),
  KEY `idx_notif_user_time` (`user_id`, `create_time`),
  KEY `idx_notif_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- xxl_job 调度库（可选，启用 XXL-JOB 时需要）
-- =============================================================================
CREATE DATABASE IF NOT EXISTS `xxl_job` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `xxl_job`;

CREATE TABLE IF NOT EXISTS `xxl_job_group` (
  `id` int NOT NULL AUTO_INCREMENT,
  `app_name` varchar(64) NOT NULL,
  `title` varchar(64) NOT NULL,
  `address_type` tinyint NOT NULL DEFAULT 0,
  `address_list` text,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_registry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `registry_group` varchar(50) NOT NULL,
  `registry_key` varchar(255) NOT NULL,
  `registry_value` varchar(255) NOT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_g_k_v` (`registry_group`,`registry_key`,`registry_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_info` (
  `id` int NOT NULL AUTO_INCREMENT,
  `job_group` int NOT NULL,
  `job_desc` varchar(255) NOT NULL,
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `author` varchar(64) DEFAULT NULL,
  `alarm_email` varchar(255) DEFAULT NULL,
  `schedule_type` varchar(50) NOT NULL DEFAULT 'NONE',
  `schedule_conf` varchar(128) DEFAULT NULL,
  `misfire_strategy` varchar(50) NOT NULL DEFAULT 'DO_NOTHING',
  `executor_route_strategy` varchar(50) DEFAULT NULL,
  `executor_handler` varchar(255) DEFAULT NULL,
  `executor_param` text,
  `executor_block_strategy` varchar(50) DEFAULT NULL,
  `executor_timeout` int NOT NULL DEFAULT 0,
  `executor_fail_retry_count` int NOT NULL DEFAULT 0,
  `glue_type` varchar(50) NOT NULL,
  `glue_source` mediumtext,
  `glue_remark` varchar(128) DEFAULT NULL,
  `glue_updatetime` datetime DEFAULT NULL,
  `child_jobid` varchar(255) DEFAULT NULL,
  `trigger_status` tinyint NOT NULL DEFAULT 0,
  `trigger_last_time` bigint NOT NULL DEFAULT 0,
  `trigger_next_time` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_group` int NOT NULL,
  `job_id` int NOT NULL,
  `executor_address` varchar(255) DEFAULT NULL,
  `executor_handler` varchar(255) DEFAULT NULL,
  `executor_param` text,
  `executor_sharding_param` varchar(20) DEFAULT NULL,
  `executor_fail_retry_count` int NOT NULL DEFAULT 0,
  `trigger_time` datetime DEFAULT NULL,
  `trigger_code` int NOT NULL,
  `trigger_msg` text,
  `handle_time` datetime DEFAULT NULL,
  `handle_code` int NOT NULL,
  `handle_msg` text,
  `alarm_status` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `I_trigger_time` (`trigger_time`),
  KEY `I_handle_code` (`handle_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_log_report` (
  `id` int NOT NULL AUTO_INCREMENT,
  `trigger_day` datetime DEFAULT NULL,
  `running_count` int NOT NULL DEFAULT 0,
  `suc_count` int NOT NULL DEFAULT 0,
  `fail_count` int NOT NULL DEFAULT 0,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_trigger_day` (`trigger_day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_logglue` (
  `id` int NOT NULL AUTO_INCREMENT,
  `job_id` int NOT NULL,
  `glue_type` varchar(50) DEFAULT NULL,
  `glue_source` mediumtext,
  `glue_remark` varchar(128) NOT NULL,
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_lock` (
  `lock_name` varchar(50) NOT NULL,
  PRIMARY KEY (`lock_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_user` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password` varchar(100) NOT NULL,
  `token` varchar(100) DEFAULT NULL,
  `role` tinyint NOT NULL,
  `permission` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `xxl_job_user` (`id`, `username`, `password`, `role`, `permission`)
VALUES (1, 'admin', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 1, NULL);

INSERT IGNORE INTO `xxl_job_lock` (`lock_name`) VALUES ('schedule_lock');

INSERT IGNORE INTO `xxl_job_group` (`id`, `app_name`, `title`, `address_type`, `update_time`)
VALUES (1, 'clouddisk-pro', 'CloudDisk Pro 执行器', 0, NOW());
