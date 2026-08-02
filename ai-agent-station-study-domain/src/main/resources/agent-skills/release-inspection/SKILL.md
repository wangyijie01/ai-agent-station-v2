---
id: release-inspection
name: 发布前后巡检
version: 1.0.0
description: 用确定性检查项完成发布前基线、发布后验证和异常回滚判定
scenes: [release, deploy, devops, inspection]
trigger-words: [发布, 上线, 部署, 巡检, 回滚, 变更, release, deploy]
allowed-tools: [query*, search*, list*, get*, fetch*, read*, describe*, deploy*, rollback*]
risk-level: MEDIUM
priority: 85
---
# 发布巡检工作流

1. 发布前固定检查：变更单、依赖、配置差异、容量、告警静默、回滚包与负责人。
2. 发布后按 1/5/15 分钟窗口观察流量、延迟、错误率、资源和核心业务成功率。
3. 阈值来自历史基线或明确 SLO，不使用模型临时编造的数字。
4. 发现异常时先输出回滚建议；真正执行发布、通知或回滚必须人工批准。
5. 输出逐项 checklist，每项必须有 PASS/FAIL/UNKNOWN、证据和下一动作。
