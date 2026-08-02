# Frontend

AI Agent Station 的 React 管理台、工作流编辑器与 Agent 运行控制台，基于 React 18、TypeScript、Rsbuild、FlowGram 和 Semi UI。

## 主要功能

- 管理 Agent、Client、Model、API、Advisor、Prompt、RAG 和 MCP 配置。
- 配置、保存和运行 Agent 工作流。
- 在 Agent 运行台选择 Skills，设置步数和工具预算。
- 新建、恢复、取消 Run，并查看 SSE 事件时间线。
- 查看运行质量评估和工具治理审计。

## 本地开发

```bash
npm ci
npm run dev
```

默认访问 `http://127.0.0.1:3002`，后端默认运行在 `http://127.0.0.1:8099`。

| 命令 | 说明 |
| --- | --- |
| `npm run dev` | 启动开发服务 |
| `npm run build` | 生成 `dist/` 生产构建 |
| `npm run lint -- --quiet` | 检查 `src/` 代码 |
| `npm run clean` | 清理构建产物 |

完整项目说明见仓库根目录 [README](../README.md)，在线介绍见 [GitHub Pages](https://wangyijie01.github.io/ai-agent-station-v2/)。
