# DeepResearch UI — 前端开发指南

> 基于 Next.js 15 + React 19 + Tailwind CSS v4 + shadcn/ui 的企业级 SPA

## 技术栈

| 层级 | 技术 | 版本 |
|:---|:---|:---|
| 框架 | Next.js (App Router) | 15.x |
| UI | React + TypeScript | 19.x / 5.x |
| 样式 | Tailwind CSS v4 + shadcn/ui | 4.x |
| 状态管理 | TanStack Query + Zustand | 5.x |
| SSE | @microsoft/fetch-event-source | 2.x |
| 图表 | Recharts | 2.x |
| 动画 | framer-motion | 11.x |
| Markdown | react-markdown + remark-gfm + rehype-highlight | 9.x |
| 数据处理 | dayjs + zod + react-hook-form | — |

## 快速开始

```bash
# 1. 安装依赖
pnpm install

# 2. 启动开发服务器
pnpm dev
# → http://localhost:3000

# 3. 构建生产版本
pnpm build

# 4. 类型检查 + Lint
pnpm typecheck
pnpm lint
```

## 环境变量

```bash
# .env.local
BACKEND_URL=http://localhost:8080            # Spring Boot 后端地址
NEXT_PUBLIC_APP_NAME=DeepResearch            # 应用名称
NEXT_PUBLIC_DEV_MODE=true                    # 开发模式（绕过 JWT 认证）
NEXT_PUBLIC_DEV_USER_ID=dev-user             # 开发用户 ID
NEXT_PUBLIC_DEV_TENANT_ID=default            # 开发租户 ID
NEXT_PUBLIC_MAX_QUERY_LENGTH=5000            # 最大查询长度
NEXT_PUBLIC_SSE_RECONNECT_MAX=5              # SSE 最大重连次数
NEXT_PUBLIC_SSE_HEARTBEAT_TIMEOUT=30000      # SSE 心跳超时 (ms)
```

## 项目结构

```
ui/
├── src/
│   ├── app/                          # Next.js App Router 页面
│   │   ├── layout.tsx                # 根布局（ThemeProvider + Navbar + Footer）
│   │   ├── page.tsx                  # 首页（Hero + ResearchInput）
│   │   ├── (research)/               # 研究路由组
│   │   │   ├── layout.tsx            # 研究页共享布局（Sidebar）
│   │   │   ├── research/[sessionId]/ # 研究详情页
│   │   │   └── history/              # 研究历史页
│   │   └── (admin)/                  # 管理后台路由组
│   │       ├── layout.tsx            # 管理布局 + 权限守卫
│   │       └── admin/prompts/        # Prompt 管理页
│   ├── components/
│   │   ├── ui/                       # shadcn/ui 基础组件（18 个）
│   │   ├── research/                 # 研究业务组件（15 个）
│   │   ├── history/                  # 历史业务组件（5 个）
│   │   ├── admin/                    # 管理业务组件（3 个）
│   │   └── layout/                   # 布局组件（Navbar/Sidebar/Footer）
│   ├── hooks/                        # 自定义 Hooks（8 个）
│   ├── stores/                       # Zustand 状态管理（3 个 store）
│   ├── lib/                          # 工具函数库
│   │   ├── types.ts                  # TypeScript 类型定义（与后端 DTO 对齐）
│   │   ├── api.ts                    # API 客户端封装
│   │   ├── sse.ts                    # SSE 客户端封装
│   │   ├── jwt.ts                    # JWT Token 管理
│   │   ├── constants.ts              # 常量配置
│   │   ├── markdown.tsx              # Markdown 渲染配置
│   │   └── utils.ts                  # 通用工具函数
│   └── providers/
│       └── app-providers.tsx         # QueryClient + Tooltip + Auth 初始化
├── docs/
│   ├── frontend-design.md            # 前端设计文档
│   └── implementation-plan.md        # 实施计划（含进度记录）
├── package.json
├── next.config.ts
├── tsconfig.json
├── tailwind.config.ts (CSS-first)
└── components.json                   # shadcn/ui 配置
```

## 路由表

| 路由 | 页面 | 类型 | 说明 |
|:---|:---|:---|:---|
| `/` | 首页 | Static | 研究输入 + 示例查询 + 最近历史 |
| `/research/[sessionId]` | 研究详情 | Dynamic | SSE 进度 + Timeline + 报告渲染 |
| `/history` | 研究历史 | Static | 搜索/筛选/分页历史列表 |
| `/admin/prompts` | Prompt 管理 | Static | 模板编辑/状态/A-B测试 |

## 后端 API 映射

| 前端方法 | 后端端点 | 说明 |
|:---|:---|:---|
| `researchApi.startResearch()` | `POST /api/research` | 发起研究 |
| `researchApi.getStatus()` | `GET /api/research/{id}` | 轮询状态 |
| `createSseConnection()` | `GET /api/research/{id}/stream` | SSE 进度流 |
| `historyApi.list()` | `GET /api/history` | 历史列表 |
| `historyApi.getDetail()` | `GET /api/history/{id}` | 历史详情 |
| `adminApi.listPrompts()` | `GET /api/admin/prompts` | Prompt 列表 |
| `adminApi.updatePrompt()` | `PUT /api/admin/prompts/{id}` | 更新模板 |
| `adminApi.resetPrompt()` | `POST /api/admin/prompts/{id}/reset` | 重置模板 |

## 开发指南

### 添加新组件

```bash
# 使用 shadcn CLI 安装组件
pnpm dlx shadcn@latest add [component-name]
```

### 调试 SSE

浏览器 DevTools → Network → 筛选 "stream" → 查看 EventStream 标签。

### 测试

```bash
pnpm test                 # 单元测试
pnpm test -- --coverage   # 覆盖率报告
pnpm exec playwright test # E2E 测试
```

## 变更记录

| 日期 | 版本 | 说明 |
|:---|:---|:---|
| 2026-07-14 | 1.0.0 | Phase 0-7 完整实施。全部页面、组件、路由、SSE 集成、后端 API 全覆盖。 |
