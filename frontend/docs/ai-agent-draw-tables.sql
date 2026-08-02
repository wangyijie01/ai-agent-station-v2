# ************************************************************
# AI Agent 拖拉拽配置数据库表设计
# 用于存储前端拖拉拽配置的agent流程图数据
# ************************************************************

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
SET NAMES utf8mb4;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE='NO_AUTO_VALUE_ON_ZERO', SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

# 转储表 ai_agent_draw_config
# ------------------------------------------------------------
# 主配置表：存储拖拉拽配置的基本信息

DROP TABLE IF EXISTS `ai_agent_draw_config`;

CREATE TABLE `ai_agent_draw_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_id` varchar(64) NOT NULL COMMENT '配置ID（唯一标识）',
  `config_name` varchar(100) NOT NULL COMMENT '配置名称',
  `description` varchar(500) DEFAULT NULL COMMENT '配置描述',
  `agent_id` varchar(64) DEFAULT NULL COMMENT '关联的智能体ID（来自ai_agent表）',
  `config_data` longtext NOT NULL COMMENT '完整的拖拉拽配置JSON数据（包含nodes和edges）',
  `version` int DEFAULT '1' COMMENT '配置版本号',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_id` (`config_id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_config_name` (`config_name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI智能体拖拉拽配置主表';

# 转储表 ai_agent_draw_nodes
# ------------------------------------------------------------
# 节点表：存储拖拉拽配置中的所有节点信息

DROP TABLE IF EXISTS `ai_agent_draw_nodes`;

CREATE TABLE `ai_agent_draw_nodes` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_id` varchar(64) NOT NULL COMMENT '配置ID（关联ai_agent_draw_config）',
  `node_id` varchar(64) NOT NULL COMMENT '节点ID（在配置中的唯一标识）',
  `node_type` varchar(32) NOT NULL COMMENT '节点类型（start、client、agent、task、advisor、prompt、model、tool_mcp、end等）',
  `node_title` varchar(100) DEFAULT NULL COMMENT '节点标题',
  `position_x` decimal(10,2) DEFAULT NULL COMMENT 'X坐标位置',
  `position_y` decimal(10,2) DEFAULT NULL COMMENT 'Y坐标位置',
  `node_data` text COMMENT '节点数据（JSON格式，包含inputs、outputs、inputsValues等）',
  `ref_id` varchar(64) DEFAULT NULL COMMENT '引用的实际资源ID（如agent_id、client_id等）',
  `ref_type` varchar(32) DEFAULT NULL COMMENT '引用的资源类型（agent、client、model、prompt、advisor、tool_mcp）',
  `sequence` int DEFAULT '0' COMMENT '节点序号（用于排序）',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_node` (`config_id`, `node_id`),
  KEY `idx_config_id` (`config_id`),
  KEY `idx_node_type` (`node_type`),
  KEY `idx_ref_id` (`ref_id`),
  KEY `idx_sequence` (`sequence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI智能体拖拉拽配置节点表';

# 转储表 ai_agent_draw_edges
# ------------------------------------------------------------
# 连线表：存储拖拉拽配置中节点之间的连接关系

DROP TABLE IF EXISTS `ai_agent_draw_edges`;

CREATE TABLE `ai_agent_draw_edges` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_id` varchar(64) NOT NULL COMMENT '配置ID（关联ai_agent_draw_config）',
  `edge_id` varchar(64) NOT NULL COMMENT '连线ID（自动生成的唯一标识）',
  `source_node_id` varchar(64) NOT NULL COMMENT '源节点ID',
  `target_node_id` varchar(64) NOT NULL COMMENT '目标节点ID',
  `source_port_id` varchar(64) DEFAULT NULL COMMENT '源端口ID（可选）',
  `target_port_id` varchar(64) DEFAULT NULL COMMENT '目标端口ID（可选）',
  `edge_type` varchar(32) DEFAULT 'default' COMMENT '连线类型（default、conditional等）',
  `edge_data` text COMMENT '连线数据（JSON格式，扩展信息）',
  `sequence` int DEFAULT '0' COMMENT '连线序号（用于排序）',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_edge` (`config_id`, `edge_id`),
  KEY `idx_config_id` (`config_id`),
  KEY `idx_source_node` (`source_node_id`),
  KEY `idx_target_node` (`target_node_id`),
  KEY `idx_sequence` (`sequence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI智能体拖拉拽配置连线表';

# 转储表 ai_agent_draw_relations
# ------------------------------------------------------------
# 关系表：存储拖拉拽配置解析后的组件关系，用于更新ai_client_config表

DROP TABLE IF EXISTS `ai_agent_draw_relations`;

CREATE TABLE `ai_agent_draw_relations` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_id` varchar(64) NOT NULL COMMENT '配置ID（关联ai_agent_draw_config）',
  `source_node_id` varchar(64) NOT NULL COMMENT '源节点ID',
  `source_type` varchar(32) NOT NULL COMMENT '源类型（model、client、agent、prompt、advisor、tool_mcp）',
  `source_ref_id` varchar(64) NOT NULL COMMENT '源引用ID（实际的资源ID）',
  `target_node_id` varchar(64) NOT NULL COMMENT '目标节点ID',
  `target_type` varchar(32) NOT NULL COMMENT '目标类型（model、client、agent、prompt、advisor、tool_mcp）',
  `target_ref_id` varchar(64) NOT NULL COMMENT '目标引用ID（实际的资源ID）',
  `relation_type` varchar(32) DEFAULT 'default' COMMENT '关系类型（default、conditional、loop等）',
  `ext_param` varchar(1024) DEFAULT NULL COMMENT '扩展参数（JSON格式）',
  `sequence` int DEFAULT '0' COMMENT '关系序号（用于排序）',
  `sync_status` tinyint(1) DEFAULT '0' COMMENT '同步状态(0:未同步,1:已同步到ai_client_config)',
  `status` tinyint(1) DEFAULT '1' COMMENT '状态(0:禁用,1:启用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_relation` (`config_id`, `source_node_id`, `target_node_id`),
  KEY `idx_config_id` (`config_id`),
  KEY `idx_source_ref` (`source_type`, `source_ref_id`),
  KEY `idx_target_ref` (`target_type`, `target_ref_id`),
  KEY `idx_sync_status` (`sync_status`),
  KEY `idx_sequence` (`sequence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI智能体拖拉拽配置关系表';

# 转储表 ai_agent_draw_history
# ------------------------------------------------------------
# 历史表：存储拖拉拽配置的变更历史

DROP TABLE IF EXISTS `ai_agent_draw_history`;

CREATE TABLE `ai_agent_draw_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_id` varchar(64) NOT NULL COMMENT '配置ID（关联ai_agent_draw_config）',
  `version` int NOT NULL COMMENT '版本号',
  `config_data` longtext NOT NULL COMMENT '历史配置JSON数据',
  `change_type` varchar(32) DEFAULT 'update' COMMENT '变更类型（create、update、delete）',
  `change_desc` varchar(500) DEFAULT NULL COMMENT '变更描述',
  `change_by` varchar(64) DEFAULT NULL COMMENT '变更人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_config_version` (`config_id`, `version`),
  KEY `idx_config_id` (`config_id`),
  KEY `idx_change_type` (`change_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI智能体拖拉拽配置历史表';

/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;