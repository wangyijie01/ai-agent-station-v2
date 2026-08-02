---
id: knowledge-grounded-answer
name: 知识库可信问答
version: 1.0.0
description: 基于 RAG 召回证据作答，控制引用、冲突和知识边界
scenes: [rag, knowledge, document, faq]
trigger-words: [知识库, 文档, 资料, 手册, 规范, rag, 引用, 根据]
allowed-tools: [search*, query*, list*, get*, fetch*, read*]
risk-level: READ_ONLY
priority: 90
---
# 知识库可信问答工作流

1. 将问题拆成可检索子问题，保留实体名、版本号、错误码和配置键等精确词。
2. 优先使用有版本、时间和来源标识的片段；冲突内容并列展示，不替用户暗自裁决。
3. 答案中的关键事实必须能回指召回证据，未被证据覆盖的内容标记为推断。
4. 召回为空或相关性不足时明确说明知识边界，并给出需要补充的文档。
5. 输出：直接答案、依据、适用版本/范围、风险与待确认项。
