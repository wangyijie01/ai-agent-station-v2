---
id: sql-performance
name: SQL 性能诊断
version: 1.0.0
description: 基于执行计划、索引、基数和慢查询证据定位 SQL 性能问题
scenes: [database, mysql, sql, performance]
trigger-words: [sql, 慢查询, 数据库, mysql, 索引, 执行计划, explain, 锁等待]
allowed-tools: [query*, explain*, show*, describe*, list*, get*, read*]
risk-level: READ_ONLY
priority: 88
---
# SQL 性能诊断工作流

1. 只使用只读查询、EXPLAIN 和元数据查看；禁止自动执行 DDL/DML。
2. 收集 SQL、参数分布、表结构、索引、数据量、执行计划和慢日志时间窗。
3. 检查全表扫描、回表、排序/临时表、基数估算、隐式转换、锁等待和连接池。
4. 优化建议要说明收益假设、副作用和验证方法，索引建议必须考虑写放大与重复索引。
5. 输出：瓶颈证据、执行计划解读、候选方案、风险、验证 SQL 与回滚方式。
