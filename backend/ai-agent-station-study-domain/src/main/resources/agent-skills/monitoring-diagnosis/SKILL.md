---
id: monitoring-diagnosis
name: 监控指标诊断
version: 1.0.0
description: 关联 QPS、RT、错误率、CPU、内存、GC 与线程池指标诊断系统异常
scenes: [devops, prometheus, grafana, monitoring, incident]
trigger-words: [监控, 指标, prometheus, grafana, cpu, 内存, gc, qps, rt, 延迟, 线程池]
allowed-tools: [query*, search*, list*, get*, fetch*, read*]
risk-level: READ_ONLY
priority: 95
---
# 监控指标诊断工作流

1. 建立基线：同环比、发布前后、故障前后，避免只看单个瞬时值。
2. 先看四个黄金信号：流量、延迟、错误、饱和度，再下钻 JVM、线程池、依赖和数据库。
3. 所有结论注明指标、时间窗、聚合维度和阈值来源。
4. 相关性不等于因果；至少补一项日志、调用链或变更证据才能升级为高置信根因。
5. 输出：异常概览、关键曲线解释、关联证据、候选根因、验证查询、处置优先级。
