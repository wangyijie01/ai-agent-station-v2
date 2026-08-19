/**
 * 前端 API 的单一配置入口。
 * 新增服务时先在这里声明端点，页面组件不直接拼接后端地址。
 */

const isDevelopment = process.env.NODE_ENV === 'development';
const isProduction = process.env.NODE_ENV === 'production';
const configuredBaseDomain = process.env.PUBLIC_API_BASE_URL?.replace(/\/$/, '');

export const API_CONFIG = {
  BASE_DOMAIN: configuredBaseDomain || (isDevelopment ? 'http://127.0.0.1:8099' : ''),
  API_VERSION: 'v1',
  TIMEOUT: 30000,
  RETRY_TIMES: 3,
} as const;

// API 端点配置
export const API_ENDPOINTS = {
  // AI 客户端相关接口
  AI_CLIENT: {
    BASE: `${API_CONFIG.BASE_DOMAIN}/api/${API_CONFIG.API_VERSION}/admin/ai-client`,
    QUERY_ALL: '/query-all',
    QUERY_ENABLED: '/query-enabled',
  },

  // AI 客户端顾问相关接口
  AI_CLIENT_ADVISOR: {
    BASE: `${API_CONFIG.BASE_DOMAIN}/api/${API_CONFIG.API_VERSION}/admin/ai-client-advisor`,
    QUERY_ALL: '/query-all',
  },

  // AI 客户端系统提示相关接口
  AI_CLIENT_SYSTEM_PROMPT: {
    BASE: `${API_CONFIG.BASE_DOMAIN}/api/${API_CONFIG.API_VERSION}/admin/ai-client-system-prompt`,
    CREATE: '/create',
    UPDATE_BY_ID: '/update-by-id',
    UPDATE_BY_PROMPT_ID: '/update-by-prompt-id',
    DELETE_BY_ID: '/delete-by-id',
    DELETE_BY_PROMPT_ID: '/delete-by-prompt-id',
    QUERY_BY_ID: '/query-by-id',
    QUERY_BY_PROMPT_ID: '/query-by-prompt-id',
    QUERY_ALL: '/query-all',
    QUERY_ENABLED: '/query-enabled',
    QUERY_BY_PROMPT_NAME: '/query-by-prompt-name',
    QUERY_LIST: '/query-list',
  },

  // AI 客户端工具MCP相关接口
  AI_CLIENT_TOOL_MCP: {
    BASE: `${API_CONFIG.BASE_DOMAIN}/api/${API_CONFIG.API_VERSION}/admin/ai-client-tool-mcp`,
    CREATE: '/create',
    UPDATE_BY_ID: '/update-by-id',
    UPDATE_BY_MCP_ID: '/update-by-mcp-id',
    DELETE_BY_ID: '/delete-by-id',
    DELETE_BY_MCP_ID: '/delete-by-mcp-id',
    QUERY_BY_ID: '/query-by-id',
    QUERY_BY_MCP_ID: '/query-by-mcp-id',
    QUERY_ALL: '/query-all',
    QUERY_BY_STATUS: '/query-by-status',
    QUERY_BY_TRANSPORT_TYPE: '/query-by-transport-type',
    QUERY_ENABLED: '/query-enabled',
    QUERY_LIST: '/query-list',
  },

  // AI 客户端模型相关接口
  AI_CLIENT_MODEL: {
    BASE: `${API_CONFIG.BASE_DOMAIN}/api/${API_CONFIG.API_VERSION}/admin/ai-client-model`,
    CREATE: '/create',
    UPDATE_BY_ID: '/update-by-id',
    UPDATE_BY_MODEL_ID: '/update-by-model-id',
    DELETE_BY_ID: '/delete-by-id',
    DELETE_BY_MODEL_ID: '/delete-by-model-id',
    QUERY_BY_ID: '/query-by-id',
    QUERY_BY_MODEL_ID: '/query-by-model-id',
    QUERY_BY_API_ID: '/query-by-api-id',
    QUERY_BY_MODEL_TYPE: '/query-by-model-type',
    QUERY_ENABLED: '/query-enabled',
    QUERY_LIST: '/query-list',
    QUERY_ALL: '/query-all',
  },

  // AI 智能体绘图相关接口
  AI_AGENT_DRAW: {
    BASE: `${API_CONFIG.BASE_DOMAIN}/api/${API_CONFIG.API_VERSION}/admin/ai-agent-draw`,
    SAVE_CONFIG: '/save-config',
    QUERY_LIST: '/query-list',
    GET_CONFIG: '/get-config',
    DELETE_CONFIG: '/delete-config',
  },

  // AI 智能体相关接口
  AI_AGENT: {
    BASE: `${API_CONFIG.BASE_DOMAIN}/api/${API_CONFIG.API_VERSION}/agent`,
    ARMORY_AGENT: '/armory_agent',
    ARMORY_API: '/armory_api',
    QUERY_AVAILABLE: '/query_available_agents',
    AUTO_AGENT: '/auto_agent',
    SKILLS: '/skills',
    SKILL_ROUTE: '/skills/route',
    RUNS: '/runs',
  },

  OPS: {
    BASE: `${API_CONFIG.BASE_DOMAIN}/api/${API_CONFIG.API_VERSION}/ops`,
  },

  // 管理员用户相关接口
  ADMIN_USER: {
    BASE: `${API_CONFIG.BASE_DOMAIN}/api/${API_CONFIG.API_VERSION}/admin/admin-user`,
    VALIDATE_LOGIN: '/validate-login',
  },

} as const;

// 请求头配置
export const DEFAULT_HEADERS = {
  'Content-Type': 'application/json',
  'Accept': 'application/json',
} as const;

// 导出便捷方法
export const getApiUrl = (endpoint: string): string => {
  return `${API_CONFIG.BASE_DOMAIN}${endpoint}`;
};

// 环境检查工具
export const ENV_UTILS = {
  isDevelopment,
  isProduction,
  isTest: process.env.NODE_ENV === 'test',
} as const;
