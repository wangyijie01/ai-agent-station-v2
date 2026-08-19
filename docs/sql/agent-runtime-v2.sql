-- Agent Runtime V2 持久化参考（MySQL 8）
-- 当前代码默认使用有界内存 RunStore，生产环境可按此表实现同一服务接口。

CREATE TABLE IF NOT EXISTS `ai_agent_skill` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `skill_id` VARCHAR(64) NOT NULL,
  `skill_name` VARCHAR(128) NOT NULL,
  `version` VARCHAR(32) NOT NULL,
  `description` VARCHAR(512) NOT NULL,
  `resource_path` VARCHAR(255) NOT NULL,
  `risk_level` VARCHAR(32) NOT NULL DEFAULT 'READ_ONLY',
  `allowed_tools` JSON NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_version` (`skill_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Skill 版本清单';

CREATE TABLE IF NOT EXISTS `ai_agent_skill_binding` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `agent_id` VARCHAR(64) NOT NULL,
  `skill_id` VARCHAR(64) NOT NULL,
  `skill_version` VARCHAR(32) NOT NULL,
  `priority` INT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_skill` (`agent_id`, `skill_id`, `skill_version`),
  KEY `idx_agent_status` (`agent_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 与 Skill 的能力边界';

CREATE TABLE IF NOT EXISTS `ai_agent_run` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `run_id` VARCHAR(64) NOT NULL,
  `trace_id` VARCHAR(64) NOT NULL,
  `session_id` VARCHAR(128) NOT NULL,
  `agent_id` VARCHAR(64) NOT NULL,
  `strategy` VARCHAR(64) NOT NULL,
  `idempotency_key` VARCHAR(128) DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL,
  `original_message` MEDIUMTEXT NOT NULL,
  `selected_skill_ids` JSON NOT NULL,
  `allowed_tools` JSON NOT NULL,
  `current_step` INT NOT NULL DEFAULT 0,
  `max_step` INT NOT NULL,
  `resume_count` INT NOT NULL DEFAULT 0,
  `waiting_question` TEXT DEFAULT NULL,
  `error_message` TEXT DEFAULT NULL,
  `create_time` DATETIME(3) NOT NULL,
  `update_time` DATETIME(3) NOT NULL,
  `complete_time` DATETIME(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_id` (`run_id`),
  UNIQUE KEY `uk_session_idempotency` (`session_id`, `idempotency_key`),
  KEY `idx_agent_status_time` (`agent_id`, `status`, `create_time`),
  KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 运行聚合';

CREATE TABLE IF NOT EXISTS `ai_agent_run_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `event_id` VARCHAR(64) NOT NULL,
  `run_id` VARCHAR(64) NOT NULL,
  `sequence_no` BIGINT NOT NULL,
  `event_type` VARCHAR(64) NOT NULL,
  `event_sub_type` VARCHAR(64) DEFAULT NULL,
  `step_no` INT DEFAULT NULL,
  `content` MEDIUMTEXT DEFAULT NULL,
  `metadata` JSON DEFAULT NULL,
  `run_status` VARCHAR(32) NOT NULL,
  `create_time` DATETIME(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`event_id`),
  UNIQUE KEY `uk_run_sequence` (`run_id`, `sequence_no`),
  KEY `idx_run_type` (`run_id`, `event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可重放的 Agent 运行事件';

CREATE TABLE IF NOT EXISTS `ai_agent_checkpoint` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `run_id` VARCHAR(64) NOT NULL,
  `stage` VARCHAR(64) NOT NULL,
  `step_no` INT NOT NULL,
  `run_status` VARCHAR(32) NOT NULL,
  `execution_summary` MEDIUMTEXT DEFAULT NULL,
  `next_action` TEXT DEFAULT NULL,
  `create_time` DATETIME(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_run_checkpoint` (`run_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 断点恢复检查点';

CREATE TABLE IF NOT EXISTS `ai_agent_tool_audit` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `audit_id` VARCHAR(64) NOT NULL,
  `run_id` VARCHAR(64) NOT NULL,
  `tool_name` VARCHAR(128) NOT NULL,
  `risk_level` VARCHAR(32) NOT NULL,
  `decision` VARCHAR(64) NOT NULL,
  `input_hash` CHAR(64) NOT NULL,
  `input_summary` VARCHAR(512) DEFAULT NULL,
  `success` TINYINT NOT NULL,
  `idempotency_hit` TINYINT NOT NULL DEFAULT 0,
  `attempt` INT NOT NULL DEFAULT 0,
  `duration_ms` BIGINT NOT NULL DEFAULT 0,
  `error_message` VARCHAR(1024) DEFAULT NULL,
  `create_time` DATETIME(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_audit_id` (`audit_id`),
  KEY `idx_run_tool_time` (`run_id`, `tool_name`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具授权与调用审计';

CREATE TABLE IF NOT EXISTS `ops_service_target` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `service_id` VARCHAR(64) NOT NULL,
  `service_name` VARCHAR(100) NOT NULL,
  `environment` VARCHAR(32) NOT NULL,
  `base_url` VARCHAR(512) NOT NULL,
  `health_path` VARCHAR(128) NOT NULL DEFAULT '/actuator/health',
  `agent_id` VARCHAR(64) DEFAULT NULL,
  `enabled` TINYINT NOT NULL DEFAULT 1,
  `interval_seconds` INT NOT NULL DEFAULT 30,
  `timeout_ms` INT NOT NULL DEFAULT 3000,
  `failure_threshold` INT NOT NULL DEFAULT 3,
  `slow_threshold_ms` BIGINT NOT NULL DEFAULT 1500,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_id` (`service_id`),
  KEY `idx_environment_enabled` (`environment`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Java 服务监督目标';

CREATE TABLE IF NOT EXISTS `ops_incident` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `incident_id` VARCHAR(64) NOT NULL,
  `service_id` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `severity` VARCHAR(32) NOT NULL,
  `summary` VARCHAR(512) NOT NULL,
  `evidence` JSON NOT NULL,
  `analysis_prompt` MEDIUMTEXT NOT NULL,
  `linked_run_id` VARCHAR(64) DEFAULT NULL,
  `occurrence_count` INT NOT NULL DEFAULT 1,
  `opened_at` DATETIME(3) NOT NULL,
  `acknowledged_at` DATETIME(3) DEFAULT NULL,
  `resolved_at` DATETIME(3) DEFAULT NULL,
  `update_time` DATETIME(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_incident_id` (`incident_id`),
  KEY `idx_service_status_time` (`service_id`, `status`, `update_time`),
  KEY `idx_linked_run` (`linked_run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Java 服务运维事件';
