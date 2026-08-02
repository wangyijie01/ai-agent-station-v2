# Backend

AI Agent Station 的 Spring AI 多模块后端，负责 Agent 编排、Skills 路由、Run 生命周期、工具治理、RAG 与管理接口。

## 模块

| 模块 | 职责 |
| --- | --- |
| `ai-agent-station-study-api` | API 契约、DTO 和统一响应 |
| `ai-agent-station-study-app` | Spring Boot 启动、资源与配置 |
| `ai-agent-station-study-domain` | Agent 编排、Skills 和 Runtime 领域逻辑 |
| `ai-agent-station-study-trigger` | HTTP 接口、任务和监听器 |
| `ai-agent-station-study-infrastructure` | DAO、Repository 和外部系统适配 |
| `ai-agent-station-study-types` | 通用类型、异常和框架组件 |

## 本地验证

```powershell
./mvnw.cmd -pl ai-agent-station-study-domain -am test
./mvnw.cmd -DskipTests compile
```

完整环境配置与启动步骤见仓库根目录 [README](../README.md#-快速开始)。
