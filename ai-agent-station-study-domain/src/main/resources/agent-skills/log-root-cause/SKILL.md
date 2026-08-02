---
id: log-root-cause
name: 日志根因分析
version: 1.0.0
description: 基于时间窗、TraceId、错误码和异常堆栈形成可追溯的日志根因结论
scenes: [devops, log, elasticsearch, elk, incident]
trigger-words: [日志, 报错, 异常, traceid, error, elk, elasticsearch, 根因]
allowed-tools: [search*, query*, list*, get*, fetch*, read*]
risk-level: READ_ONLY
priority: 100
---
# 日志根因分析工作流

1. 先确认环境、服务、时间窗和关联标识；缺一项且会显著扩大查询范围时，进入 WAIT_USER_INPUT。
2. 先做错误分布与趋势粗查，再按 TraceId、错误码或异常类型下钻，禁止无边界全量拉日志。
3. 证据至少包含时间、服务、关键字段、原始日志摘要和查询来源；敏感字段必须脱敏。
4. 将“工具返回的事实”和“模型推断”分开，推断要给置信度与反证条件。
5. 输出固定为：现象、时间线、证据、候选根因、验证动作、修复与回滚建议。
6. 查询无结果时扩大范围最多两次；仍无证据就明确停止，不得臆造根因。
