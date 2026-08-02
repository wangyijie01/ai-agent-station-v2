---
id: incident-response
name: 线上故障应急响应
version: 1.0.0
description: 编排监控、日志、变更和知识证据，生成可执行的故障响应方案
scenes: [devops, incident, oncall, root-cause]
trigger-words: [线上故障, 故障, 宕机, 告警, 应急, oncall, 止损, 回滚]
allowed-tools: [query*, search*, list*, get*, fetch*, read*, describe*, rollback*, restart*]
risk-level: MEDIUM
priority: 110
---
# 线上故障应急响应工作流

1. 先分级影响面和止损优先级，再进行根因分析；不得为了找根因延误止损建议。
2. 收集监控、日志、发布变更、依赖状态四类证据，形成按时间排序的事件线。
3. 任何写操作、通知、扩缩容、重启或回滚都属于高风险动作，必须等待人工批准。
4. 每个动作写明预期效果、风险、回滚条件、负责人和验证指标。
5. 输出：影响面、当前状态、证据时间线、候选根因、止损方案、恢复验证、复盘待办。
