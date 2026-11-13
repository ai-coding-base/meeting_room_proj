-- =====================================================================
-- 会议室预订系统 v1.0 · DB Schema (MySQL 8.0+)
-- 依据：requirement_review.md 第 6/7 章 + prd.md
-- 设计要点：
--   1. 全字段软删除（deleted_at）
--   2. TimeSlot 预生成 + (room_id, slot_start) 唯一约束兜底超卖
--   3. idempotency_keys 表实现客户端 request_id 幂等
--   4. 审计日志独立表
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 用户表（本系统独立维护，不对接 SSO）
-- ---------------------------------------------------------------------
CREATE TABLE `users` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username`      VARCHAR(64)  NOT NULL COMMENT '登录名',
  `display_name`  VARCHAR(64)  NOT NULL COMMENT '显示名',
  `email`         VARCHAR(128) NOT NULL,
  `phone`         VARCHAR(32)  DEFAULT NULL,
  `wecom_userid`  VARCHAR(64)  DEFAULT NULL COMMENT '企微 userid',
  `password_hash` VARCHAR(255) NOT NULL,
  `department_id` BIGINT UNSIGNED DEFAULT NULL,
  `superior_id`   BIGINT UNSIGNED DEFAULT NULL COMMENT '直接上级；手动配置',
  `role`          ENUM('EMPLOYEE','ASSISTANT','MANAGER','APPROVER','ADMIN','RECEPTIONIST','AUDITOR') NOT NULL DEFAULT 'EMPLOYEE',
  `is_active`     TINYINT(1) NOT NULL DEFAULT 1,
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`    DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  KEY `idx_superior` (`superior_id`),
  KEY `idx_department` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工表';

-- ---------------------------------------------------------------------
-- 2. 部门表
-- ---------------------------------------------------------------------
CREATE TABLE `departments` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(64) NOT NULL,
  `parent_id`  BIGINT UNSIGNED DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- ---------------------------------------------------------------------
-- 3. 代订授权表（行政/助理 ↔ 被代订人）
-- ---------------------------------------------------------------------
CREATE TABLE `delegation_grants` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `delegate_id`  BIGINT UNSIGNED NOT NULL COMMENT '代订人',
  `principal_id` BIGINT UNSIGNED NOT NULL COMMENT '被代订人（领导）',
  `valid_from`   DATE DEFAULT NULL,
  `valid_to`     DATE DEFAULT NULL,
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at`   DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_delegate_principal` (`delegate_id`, `principal_id`, `deleted_at`),
  KEY `idx_principal` (`principal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代订授权关系';

-- ---------------------------------------------------------------------
-- 4. 会议室表
-- ---------------------------------------------------------------------
CREATE TABLE `rooms` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`          VARCHAR(64) NOT NULL,
  `capacity`      INT UNSIGNED NOT NULL COMMENT '人数容量；> 50 自动走审批',
  `floor`         VARCHAR(32) DEFAULT NULL,
  `area`          VARCHAR(64) DEFAULT NULL,
  `equipment`     JSON DEFAULT NULL COMMENT '["projector","video_conf","whiteboard"]',
  `is_vip`        TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'VIP 强制走审批',
  `department_id` BIGINT UNSIGNED DEFAULT NULL COMMENT 'NULL=公共；非空=部门专属',
  `status`        ENUM('AVAILABLE','MAINTENANCE','DECOMMISSIONED') NOT NULL DEFAULT 'AVAILABLE',
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`    DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`, `deleted_at`),
  KEY `idx_status` (`status`),
  KEY `idx_department` (`department_id`),
  KEY `idx_capacity` (`capacity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议室';

-- ---------------------------------------------------------------------
-- 5. 时间槽占用表（预生成 + 唯一约束兜底超卖）
--    每条记录 = 一个 30 分钟槽位的占用
--    一次预订时长跨多个槽位时，会写入多行（同一 booking_id）
-- ---------------------------------------------------------------------
CREATE TABLE `time_slots` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `room_id`    BIGINT UNSIGNED NOT NULL,
  `slot_start` DATETIME NOT NULL COMMENT '槽位开始时间（必须为 :00 或 :30）',
  `slot_end`   DATETIME NOT NULL COMMENT 'slot_start + 30min',
  `booking_id` BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  -- 核心唯一约束：保证同一会议室同一槽位不会被两个预订占用
  UNIQUE KEY `uk_room_slot` (`room_id`, `slot_start`),
  KEY `idx_booking` (`booking_id`),
  KEY `idx_room_range` (`room_id`, `slot_start`, `slot_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='时间槽占用（释放即物理删除）';

-- ---------------------------------------------------------------------
-- 6. 预订表
-- ---------------------------------------------------------------------
CREATE TABLE `bookings` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `room_id`        BIGINT UNSIGNED NOT NULL,
  `booker_id`      BIGINT UNSIGNED NOT NULL COMMENT '实际操作人',
  `principal_id`   BIGINT UNSIGNED NOT NULL COMMENT '被代订人；非代订时 = booker_id',
  `subject`        VARCHAR(255) NOT NULL COMMENT '会议主题',
  `attendees`      JSON DEFAULT NULL COMMENT '参会人 user_id 列表',
  `attendee_count` INT UNSIGNED NOT NULL,
  `start_time`     DATETIME NOT NULL,
  `end_time`       DATETIME NOT NULL,
  `visibility`     ENUM('PUBLIC','PRIVATE') NOT NULL DEFAULT 'PUBLIC' COMMENT '默认全员可见',
  `status`         ENUM('PENDING','CONFIRMED','REJECTED','CANCELLED','COMPLETED') NOT NULL,
  `approver_id`    BIGINT UNSIGNED DEFAULT NULL,
  `approved_at`    DATETIME DEFAULT NULL,
  `reject_reason`  VARCHAR(500) DEFAULT NULL,
  `cancelled_by`   BIGINT UNSIGNED DEFAULT NULL,
  `cancelled_at`   DATETIME DEFAULT NULL,
  `cancel_reason`  VARCHAR(500) DEFAULT NULL COMMENT '上级强制取消必填',
  `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`     DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_room_time` (`room_id`, `start_time`, `end_time`),
  KEY `idx_principal_time` (`principal_id`, `start_time`),
  KEY `idx_booker_time`    (`booker_id`, `start_time`),
  KEY `idx_status` (`status`),
  CONSTRAINT `chk_time_order` CHECK (`end_time` > `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预订主表';

-- ---------------------------------------------------------------------
-- 7. 幂等表（客户端 request_id 防重复提交）
-- ---------------------------------------------------------------------
CREATE TABLE `idempotency_keys` (
  `request_id`  VARCHAR(64) NOT NULL COMMENT '客户端 UUID',
  `user_id`     BIGINT UNSIGNED NOT NULL,
  `endpoint`    VARCHAR(64) NOT NULL COMMENT '如 POST /bookings',
  `booking_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '首次执行结果',
  `response`    JSON DEFAULT NULL COMMENT '完整响应快照',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at`  DATETIME NOT NULL COMMENT 'TTL 24h；定时清理',
  PRIMARY KEY (`request_id`),
  KEY `idx_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等键';

-- ---------------------------------------------------------------------
-- 8. 审计日志表
-- ---------------------------------------------------------------------
CREATE TABLE `audit_logs` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `operator_id` BIGINT UNSIGNED NOT NULL,
  `action`      VARCHAR(64) NOT NULL COMMENT 'BOOKING_CREATE / BOOKING_CANCEL / ROOM_UPDATE ...',
  `target_type` VARCHAR(32) NOT NULL COMMENT 'booking / room / user',
  `target_id`   BIGINT UNSIGNED NOT NULL,
  `detail`      JSON DEFAULT NULL COMMENT '前后状态快照、原因、IP 等',
  `ip`          VARCHAR(64) DEFAULT NULL,
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_operator_time` (`operator_id`, `created_at`),
  KEY `idx_target` (`target_type`, `target_id`),
  KEY `idx_action_time` (`action`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';

-- ---------------------------------------------------------------------
-- 9. 通知发送记录表（用于重试与到达率统计）
-- ---------------------------------------------------------------------
CREATE TABLE `notifications` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `recipient_id` BIGINT UNSIGNED NOT NULL,
  `channel`      ENUM('EMAIL','WECOM','SMS') NOT NULL,
  `event`        VARCHAR(64) NOT NULL COMMENT 'BOOKING_CREATED / FORCE_CANCELLED ...',
  `booking_id`   BIGINT UNSIGNED DEFAULT NULL,
  `payload`      JSON NOT NULL,
  `status`       ENUM('PENDING','SENT','FAILED') NOT NULL DEFAULT 'PENDING',
  `retry_count`  INT NOT NULL DEFAULT 0,
  `sent_at`      DATETIME DEFAULT NULL,
  `error`        VARCHAR(500) DEFAULT NULL,
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_recipient` (`recipient_id`, `created_at`),
  KEY `idx_status_retry` (`status`, `retry_count`),
  KEY `idx_booking` (`booking_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知发送记录';
