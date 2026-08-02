# AI Agent Station V2

基于 Java 17、Spring Boot 3.4、Spring AI 与 DDD 分层实现的可治理 Agent Runtime。项目在原有 Auto / Flow / Fixed 三种执行策略上，补齐了文件型 Skills、运行状态机、幂等与断点恢复、工具权限治理、事件审计、质量评估和 Prometheus 指标。

[在线项目介绍](https://wangyijie01.github.io/ai-agent-station-v2/) · [后端仓库](https://github.com/wangyijie01/ai-agent-station-v2) · [前端运行台](https://github.com/wangyijie01/ai-agent-station-console) · [完整 V2 设计](docs/agent-v2-design.md)

## 为什么做这次改造

传统 Agent Demo 往往能循环调用模型和工具，但缺少可控的运行身份、状态边界与副作用治理。V2 的目标是把非确定性的模型行为装进确定性的工程边界：

- 场景方法论由 Skill 管理，与通用执行引擎解耦。
- 每次执行成为有 runId、traceId、状态和事件的 Run。
- 工具调用必须经过白名单、风险、授权、预算、幂等和熔断校验。
- Run 可以等待用户补充信息、恢复、取消，并能解释发生过什么。
- 关键确定性逻辑由不依赖真实模型的单元测试覆盖。

## 核心能力

| 能力 | 当前实现 |
| --- | --- |
| 文件型 Skills | 从 <code>agent-skills/*/SKILL.md</code> 加载并校验；支持显式指定和可解释的触发词/场景路由 |
| 结构化决策 | 优先解析 JSON 动作契约，并保留旧版中文标记作为迁移兜底 |
| Run 生命周期 | <code>CREATED → RUNNING → WAITING_USER_INPUT / SUCCEEDED / FAILED / CANCELED</code> |
| 幂等与恢复 | 客户端幂等键避免重复 Run；检查点记录阶段摘要；等待或失败的 Run 可按原 runId 恢复 |
| 工具治理 | Skill 白名单、风险分级、高风险授权、调用预算、幂等去重、只读重试、熔断和脱敏审计 |
| 事件与评估 | SSE 事件携带 runId、traceId 和严格递增序号；支持查询检查点、审计与运行质量评估 |
| 可观测性 | Spring Boot Actuator + Prometheus，记录 Run 转换、耗时和工具调用指标 |
| React 运行台 | 支持启动、恢复、取消、Skill 选择、事件时间线、评估与工具审计 |

## 架构

    React Agent Console
            │ HTTP + SSE
            ▼
    AiAgentController
            │
            ├── Run Runtime ── 状态机 / 幂等 / 检查点 / 事件 / 评估
            ├── Skill Router ── 文件加载 / 校验 / 可解释路由
            └── Execute Strategy ── Auto / Flow / Fixed
                         │
                         ▼
                 Spring AI ChatClient
                         │
                         ▼
               Governed ToolCallback
                         │ allowlist / risk / approval / budget
                         ▼
                      MCP Tools

详细的状态图、时序图、数据模型、工程取舍和面试表达见 [docs/agent-v2-design.md](docs/agent-v2-design.md)。生产持久化表结构草案见 [docs/sql/agent-runtime-v2.sql](docs/sql/agent-runtime-v2.sql)。

## 内置 Skills

当前提供 6 个可扩展的文件型 Skill：

- <code>log-root-cause</code>：日志根因分析
- <code>monitoring-diagnosis</code>：监控指标诊断
- <code>knowledge-grounded-answer</code>：基于知识证据的问答
- <code>incident-response</code>：线上故障应急响应
- <code>release-inspection</code>：发布前后巡检
- <code>sql-performance</code>：SQL 性能诊断

每个 <code>SKILL.md</code> 的 front matter 声明 ID、版本、场景、触发词、工具白名单、风险级别和优先级；正文承载该场景的工作流约束。新增 Skill 不需要修改执行节点。

## Agent API

基础路径：<code>/api/v1/agent</code>

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | <code>/auto_agent</code> | 启动或恢复 Agent，返回 SSE |
| GET | <code>/skills</code> | 查询全部 Skills |
| POST | <code>/skills/route</code> | 预览 Skill 路由结果 |
| GET | <code>/runs/{runId}</code> | 查询运行快照 |
| GET | <code>/runs/{runId}/events</code> | 查询有序事件 |
| GET | <code>/runs/{runId}/checkpoints</code> | 查询检查点 |
| GET | <code>/runs/{runId}/evaluation</code> | 查询运行质量评估 |
| GET | <code>/runs/{runId}/tool-audits</code> | 查询工具治理审计 |
| POST | <code>/runs/{runId}/cancel</code> | 取消运行 |

启动请求示例：

    {
      "aiAgentId": "3",
      "message": "分析 payment 服务最近 30 分钟的超时告警，先给证据再给根因",
      "sessionId": "session-20260801-001",
      "idempotencyKey": "incident-payment-20260801-001",
      "requestedSkillIds": ["log-root-cause", "monitoring-diagnosis"],
      "approvedToolNames": [],
      "maxStep": 6,
      "maxToolCalls": 8
    }

高风险工具只有在同时命中 Skill 白名单和可信授权时才会放行。当前请求字段用于本地演示；生产环境必须由服务端审批系统签发并校验授权票据。

## 本地运行

环境要求：

- JDK 17
- MySQL 8
- PostgreSQL + pgvector
- OpenAI 兼容模型服务
- Node.js 20+（运行前端时需要）

仓库包含 Maven Wrapper。PowerShell 示例：

    $env:OPENAI_API_KEY = "<your-api-key>"
    $env:MYSQL_URL = "jdbc:mysql://127.0.0.1:13306/ai-agent-station-study?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
    $env:PGVECTOR_URL = "jdbc:postgresql://127.0.0.1:5432/springai"

    .\mvnw.cmd -pl ai-agent-station-study-app -am install -DskipTests
    .\mvnw.cmd -f ai-agent-station-study-app\pom.xml spring-boot:run

默认 <code>dev</code> 端口为 <code>8099</code>：

- 健康检查：<code>GET http://127.0.0.1:8099/actuator/health</code>
- Prometheus：<code>GET http://127.0.0.1:8099/actuator/prometheus</code>

## 验证

    # 不依赖数据库、真实模型或 MCP 的领域单元测试
    .\mvnw.cmd -pl ai-agent-station-study-domain -am test

    # 完整多模块编译
    .\mvnw.cmd -DskipTests compile

领域测试覆盖 Skill 路由、结构化决策解析、状态转换、幂等、等待与恢复，以及工具白名单、授权、预算、重试和审计。历史 app 测试多为依赖外部系统的集成测试，仍按原配置默认跳过。

## 当前边界

- Run、事件、检查点和审计当前使用有界内存实现，适合演示领域语义；应用重启后不会保留，也不支持多实例一致性。
- <code>docs/sql/agent-runtime-v2.sql</code> 已给出生产表模型，但尚未接入 Repository。
- 生产环境应增加 MySQL 持久化、Redis 幂等与分布式锁、消息队列或工作流引擎，以及企业 RBAC/审批票据。
- 工具能力取决于实际装配的 MCP 服务；Skill 白名单只定义治理边界，不代表工具一定存在。
- 前端当前以 ESLint 和生产构建为验收门槛，尚未配置自动化测试。

## 安全说明

配置文件仅保留环境变量占位符，不应提交真实 API Key、数据库密码或令牌。历史样例中的旧密钥即使在当前工作树中已替换，也可能仍存在于 Git 历史，因此对应凭据必须轮换。工具审计会对常见敏感字段脱敏并保存 SHA-256 摘要，但生产日志仍需接入统一的数据分级、留存和访问控制策略。

## 来源与改造说明

本项目基于 KnowledgePlanet 的 [ai-agent-station-study](https://gitcode.net/KnowledgePlanet/ai-agent-station-study) 学习项目继续扩展，保留原作者注释与署名。V2 的 Skills、Run Runtime、工具治理、运行台、测试、设计文档和展示页由 [wangyijie01](https://github.com/wangyijie01) 完成。公开使用时请同时遵守上游项目的授权与版权要求。
