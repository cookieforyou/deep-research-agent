# DeepResearch 前端设计文档

> 版本: 1.0.0 | 日期: 2026-07-14 | 状态: 设计阶段

---

## 目录

1. [设计目标与原则](#1-设计目标与原则)
2. [技术选型](#2-技术选型)
3. [项目架构](#3-项目架构)
4. [路由设计](#4-路由设计)
5. [组件树与页面布局](#5-组件树与页面布局)
6. [数据流与状态管理](#6-数据流与状态管理)
7. [SSE 实时通信设计](#7-sse-实时通信设计)
8. [API 对接设计](#8-api-对接设计)
9. [页面详细设计](#9-页面详细设计)
10. [Markdown 报告渲染](#10-markdown-报告渲染)
11. [认证与安全](#11-认证与安全)
12. [主题与国际化](#12-主题与国际化)
13. [性能优化策略](#13-性能优化策略)
14. [测试策略](#14-测试策略)
15. [实施计划](#15-实施计划)

---

## 1. 设计目标与原则

### 1.1 核心目标

构建一个**企业级单页应用 (SPA)**，为 DeepResearch Multi-Agent 系统提供直观、实时、可交互的用户界面：

- 用户输入研究查询，实时观看 AI 工作流进度
- 查看格式化的 Markdown 深度研报（含引用溯源）
- 浏览研究历史，对比评估分数
- 管理 Prompt 模板（管理员）

### 1.2 设计原则

| 原则 | 说明 |
|:---|:---|
| **实时性优先** | SSE 流式推送 → UI 即时反映工作流节点进度（7阶段可视化） |
| **渐进式展示** | 长报告分段加载；大纲导航；引用可点击跳转 |
| **容错降级** | 网络断开自动重连 SSE；API 失败显示优雅错误状态 |
| **移动端适配** | 响应式布局，手机端同样可用 |
| **可访问性** | 语义化 HTML、键盘导航、焦点管理 |
| **性能** | 代码分割、虚拟列表、SSE 背压兼容 |

---

## 2. 技术选型

### 2.1 技术栈

| 层级 | 技术 | 版本 | 选型理由 |
|:---|:---|:---|:---|
| **框架** | Next.js (App Router) | 15.x | React 生态最流行；SSR/SSG/ISR 灵活；Server Components 减少客户端 JS |
| **语言** | TypeScript | 5.x | 类型安全，与 Java Record 后端类型对齐 |
| **UI 组件库** | shadcn/ui | latest | 基于 Radix UI + Tailwind，无包依赖，源码可控，定制性强 |
| **样式** | Tailwind CSS | 4.x | 原子化 CSS，与 shadcn/ui 深度集成 |
| **服务端状态** | TanStack Query | 5.x | 缓存/重试/轮询/乐观更新，SSE 流式数据管理 |
| **客户端状态** | Zustand | 5.x | 轻量（<1KB），无 boilerplate，适合 SSE 连接状态管理 |
| **SSE 客户端** | @microsoft/fetch-event-source | 2.x | 比原生 EventSource 更好的错误处理 + POST 支持 + 自定义 headers |
| **Markdown 渲染** | react-markdown + remark-gfm + rehype-raw | latest | 安全渲染，GFM 语法支持，自定义组件（引用高亮） |
| **代码高亮** | rehype-highlight + Shiki | latest | 报告内代码块语法高亮 |
| **图表** | Recharts | 2.x | React 原生，声明式 API，评估分数雷达图/历史趋势 |
| **表格** | @tanstack/react-table | 8.x | Headless，灵活，适合研究历史列表 |
| **表单验证** | react-hook-form + zod | latest | 类型安全的表单校验，与 Java Bean Validation 对应 |
| **动画** | framer-motion | 11.x | 工作流节点过渡动画，微交互 |
| **日期处理** | dayjs | 1.x | 轻量（2KB），ISO 8601 兼容 |
| **HTTP 客户端** | fetch (native) + fetch-event-source | - | 零依赖，Next.js 内置 polyfill |
| **Toast 通知** | sonner | latest | 极简 API，支持 rich colors |

### 2.2 开发工具

| 工具 | 用途 |
|:---|:---|
| **pnpm** | 包管理器（严格依赖树，磁盘高效） |
| **ESLint** | 代码规范（flat config） |
| **Prettier** | 代码格式化 |
| **Husky + lint-staged** | Git hooks 预提交检查 |
| **Vitest** | 单元测试（Vite 原生，快速） |
| **Playwright** | E2E 测试 |
| **Storybook** | 组件开发与文档（可选） |

---

## 3. 项目架构

### 3.1 目录结构

```
ui/
├── .env.local                         # 环境变量（API Base URL 等）
├── .env.example                       # 环境变量模板
├── next.config.ts                     # Next.js 配置（rewrite proxy 到后端）
├── tailwind.config.ts                 # Tailwind 配置（shadcn/ui 主题变量）
├── tsconfig.json                      # TypeScript 配置（strict + path aliases）
├── package.json
├── components.json                    # shadcn/ui 配置文件
├── public/
│   └── logo.svg                       # 品牌 Logo
└── src/
    ├── app/                           # Next.js App Router 页面
    │   ├── layout.tsx                 # 根布局（Providers + Navbar + Theme）
    │   ├── page.tsx                   # 首页（研究输入入口）
    │   ├── (research)/                # 研究路由组（共享 ResearchLayout）
    │   │   ├── layout.tsx             # 研究侧边栏布局
    │   │   ├── [sessionId]/           # 研究详情（SSE + 报告渲染）
    │   │   │   └── page.tsx
    │   │   └── history/               # 研究历史列表
    │   │       └── page.tsx
    │   ├── (admin)/                   # 管理后台路由组（需 admin 角色）
    │   │   ├── layout.tsx             # 管理布局
    │   │   ├── prompts/               # Prompt 模板管理
    │   │   │   └── page.tsx
    │   │   └── users/                 # 用户管理（预留）
    │   │       └── page.tsx
    │   └── api/                       # Next.js API Routes（BFF 层，可选）
    │       └── auth/                  # Token 刷新等代理接口
    ├── components/                    # 通用组件
    │   ├── ui/                        # shadcn/ui 生成的基础组件
    │   │   ├── button.tsx
    │   │   ├── card.tsx
    │   │   ├── dialog.tsx
    │   │   ├── input.tsx
    │   │   ├── select.tsx
    │   │   ├── skeleton.tsx
    │   │   ├── toast.tsx
    │   │   ├── badge.tsx
    │   │   ├── progress.tsx
    │   │   ├── tabs.tsx
    │   │   ├── table.tsx
    │   │   ├── tooltip.tsx
    │   │   ├── dropdown-menu.tsx
    │   │   ├── sheet.tsx
    │   │   └── ...
    │   ├── research/                  # 研究相关组件
    │   │   ├── ResearchInput.tsx      # 研究查询输入框
    │   │   ├── WorkflowTimeline.tsx   # 工作流阶段时间线
    │   │   ├── WorkflowNode.tsx       # 单个工作流节点卡片
    │   │   ├── SseStatusBadge.tsx     # SSE 连接状态指示器
    │   │   ├── ReportViewer.tsx       # Markdown 报告渲染器
    │   │   ├── ReportOutline.tsx      # 报告大纲侧边导航
    │   │   ├── CitationPopover.tsx    # 引用浮窗（悬停显示来源详情）
    │   │   ├── EvidenceDrawer.tsx     # 证据抽屉（右滑面板）
    │   │   ├── EvalScoreCard.tsx      # 评估分数卡片
    │   │   ├── EvalRadarChart.tsx     # 评估雷达图
    │   │   └── CacheHitBanner.tsx     # 缓存命中通知横幅
    │   ├── history/                   # 历史相关组件
    │   │   ├── HistoryList.tsx        # 研究历史列表
    │   │   ├── HistoryCard.tsx        # 单条历史卡片
    │   │   ├── HistorySearch.tsx      # 历史搜索栏
    │   │   └── HistoryFilters.tsx     # 筛选器（状态/日期/评分）
    │   ├── admin/                     # 管理后台组件
    │   │   ├── PromptEditor.tsx       # Prompt 模板编辑器
    │   │   ├── PromptDiffModal.tsx    # Prompt 版本对比弹窗
    │   │   └── AbTestConfig.tsx       # A/B 测试配置
    │   └── layout/                    # 布局组件
    │       ├── Navbar.tsx             # 顶部导航栏
    │       ├── Sidebar.tsx            # 侧边栏（研究上下文）
    │       ├── Breadcrumb.tsx         # 面包屑导航
    │       └── Footer.tsx             # 页脚
    ├── hooks/                         # 自定义 Hooks
    │   ├── useResearchSse.ts          # SSE 连接 + 进度事件管理
    │   ├── useResearchQuery.ts        # 发起研究 + 状态轮询
    │   ├── useReportStream.ts         # 报告内容流式更新（预留）
    │   ├── useHistoryList.ts          # 研究历史分页查询
    │   └── useAuth.ts                 # JWT Token 管理
    ├── lib/                           # 工具函数
    │   ├── api.ts                     # API 客户端（fetch 封装 + JWT 拦截）
    │   ├── sse.ts                     # SSE 客户端封装（自动重连 + 心跳）
    │   ├── types.ts                   # 前端类型定义（与后端 DTO 对齐）
    │   ├── constants.ts               # 常量（研究阶段映射、颜色等）
    │   ├── markdown.ts                # Markdown 渲染配置
    │   ├── jwt.ts                     # JWT 解析与刷新
    │   └── utils.ts                   # 通用工具（cn() 等）
    └── stores/                        # Zustand 状态管理
        ├── auth-store.ts              # 认证状态（userId/tenantId/token）
        ├── sse-store.ts               # SSE 连接状态（各 sessionId 的连接）
        └── ui-store.ts                # UI 状态（侧边栏/主题）
```

### 3.2 项目初始化命令

```bash
cd ui/
pnpm create next-app@latest . --typescript --tailwind --eslint --app --src-dir --import-alias "@/*"
pnpm add @microsoft/fetch-event-source react-markdown remark-gfm rehype-raw rehype-highlight
pnpm add zustand @tanstack/react-query recharts dayjs framer-motion sonner
pnpm add react-hook-form @hookform/resolvers zod
pnpm add -D @types/react @types/node vitest @testing-library/react playwright
pnpm dlx shadcn@latest init  # 按提示选择 Tailwind v4 + CSS variables
pnpm dlx shadcn@latest add button card dialog input select skeleton badge progress tabs table tooltip dropdown-menu sheet toast
```

---

## 4. 路由设计

### 4.1 路由表

| 路由 | 页面 | 认证 | 说明 |
|:---|:---|:---|:---|
| `/` | 首页 | 需要 | 研究查询输入 + 快速开始 |
| `/research/[sessionId]` | 研究详情 | 需要 | SSE 进度 + Markdown 研报 |
| `/history` | 研究历史 | 需要 | 分页列表 + 搜索 + 筛选 |
| `/admin/prompts` | Prompt 管理 | Admin | 模板列表 + 编辑 + A/B 配置 |
| `/admin/users` | 用户管理 | Admin | 预留 |

### 4.2 路由布局

```
RootLayout (Providers + Navbar + ThemeProvider)
├── /                         → 首页（研究输入卡片）
├── /research/[sessionId]     → 研究详情（SSE 进度 + 报告 + 侧边栏）
├── /history                  → 历史页（表格 + 筛选器）
├── /admin/prompts            → Prompt 管理（表格 + 编辑器）
└── /admin/users              → 用户管理（预留）
```

### 4.3 Next.js 配置（Proxy 后端 API）

```typescript
// next.config.ts
const nextConfig = {
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${process.env.BACKEND_URL}/api/:path*`,
      },
      {
        source: '/actuator/:path*',
        destination: `${process.env.BACKEND_URL}/actuator/:path*`,
      },
    ]
  },
}
```

---

## 5. 组件树与页面布局

### 5.1 首页布局

```
┌─────────────────────────────────────────────────────┐
│  Navbar (Logo | 历史 | Admin | 用户头像 | 主题切换) │
├─────────────────────────────────────────────────────┤
│                                                     │
│          ┌──────────────────────────┐               │
│          │      Hero Section        │               │
│          │  DeepResearch 深度研究   │               │
│          │  企业级 AI 多智能体系统  │               │
│          └──────────────────────────┘               │
│                                                     │
│          ┌──────────────────────────┐               │
│          │    ResearchInput Card    │               │
│          │  ┌────────────────────┐  │               │
│          │  │  🔍 输入研究主题...│  │               │
│          │  └────────────────────┘  │               │
│          │  [深度研究] [直接回答]   │               │
│          │         [开始研究 →]     │               │
│          └──────────────────────────┘               │
│                                                     │
│          ┌──────────────────────────┐               │
│          │    快速开始 / 示例查询   │               │
│          │  · 2026年新能源市场趋势  │               │
│          │  · AI芯片行业竞争格局    │               │
│          │  · 光伏产业技术路线分析  │               │
│          └──────────────────────────┘               │
│                                                     │
│          ┌──────────────────────────┐               │
│          │    最近研究历史 (3条)    │               │
│          │  HistoryCard × 3         │               │
│          └──────────────────────────┘               │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 5.2 研究详情页布局

```
┌─────────────────────────────────────────────────────┐
│  Navbar                                             │
├───────────┬─────────────────────────────────────────┤
│  Sidebar  │         主内容区                        │
│           │                                         │
│  📋 研究  │  ┌─────────────────────────────────┐    │
│  上下文   │  │  WorkflowTimeline               │    │
│           │  │  ○ intent_route ✓              │    │
│  · 查询   │  │  ○ plan ✓                      │    │
│  · 子问题 │  │  ◉ dual_search (进行中...)      │    │
│  · 证据数 │  │  ○ filter                       │    │
│  · 耗时   │  │  ○ analyze                      │    │
│           │  │  ○ write                        │    │
│  📊 评估  │  └─────────────────────────────────┘    │
│  (完成后  │                                         │
│   显示)   │  ┌────────────────────────────────────┐ │
│           │  │  ReportViewer (Tab 切换)           │ │
│  📑 大纲  │  │  [完整报告] [结论速览] [证据列表]  │ │
│  (完成后  │  │  ────────────────────────────────  │ │
│   显示)   │  │  # 2026年中国新能源汽车...         │ │
│           │  │  ## 1. 市场概况                    │ │
│           │  │  ...（Markdown 渲染）              │ │
│           │  │  引用[1] 引用[2]                   │ │
│           │  └────────────────────────────────────┘ │
│           │                                         │
└───────────┴─────────────────────────────────────────┘
   (移动端: Sidebar → 顶部 Tab 切换)
```

### 5.3 研究历史页布局

```
┌──────────────────────────────────────────────────┐
│  Navbar                                          │
├──────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────┐ │
│  │  研究历史                                   │ │
│  │  ┌──────────────┬──────────┬─────────────┐  │ │
│  │  │ 🔍 搜索...   │ 状态 ▼   │ 日期范围 ▼  │  │ │
│  │  └──────────────┴──────────┴─────────────┘  │ │
│  └─────────────────────────────────────────────┘ │
│                                                  │
│  ┌─────────────────────────────────────────────┐ │
│  │  HistoryCard 1                              │ │
│  │  📝 "2026年新能源市场趋势" — COMPLETED      │ │
│  │  ⭐ 4.2 分 | 2,136 字 | 45 引用 | 2min前    │ │
│  │  EvalRadarChart (mini)                      │ │
│  └─────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────┐ │
│  │  HistoryCard 2 ...                          │ │
│  └─────────────────────────────────────────────┘ │
│                                                  │
│  [上一页] 1 / 10 [下一页]                        │
└──────────────────────────────────────────────────┘
```

---

## 6. 数据流与状态管理

### 6.1 状态分层

```
┌──────────────────────────────────────┐
│         TanStack Query               │  ← 服务端状态
│  (研究列表、报告内容、Prompt列表)    │    自动缓存/重试/重新验证
├──────────────────────────────────────┤
│         Zustand Stores               │  ← 客户端状态
│  auth-store (token, userId, tenantId)│    SSE连接、认证、UI偏好
│  sse-store  (connections, events)    │
│  ui-store   (sidebar, theme)         │
└──────────────────────────────────────┘
```

### 6.2 Zustand Store 设计

```typescript
// stores/auth-store.ts
interface AuthState {
  token: string | null;
  userId: string;
  tenantId: string;
  refreshToken: string | null;
  setToken: (token: string) => void;
  logout: () => void;
  isAuthenticated: () => boolean;
}

// stores/sse-store.ts
interface SseState {
  connections: Map<string, SseConnection>;  // sessionId → connection
  events: Map<string, ProgressEvent[]>;      // sessionId → buffered events
  connect: (sessionId: string) => void;
  disconnect: (sessionId: string) => void;
  getEvents: (sessionId: string) => ProgressEvent[];
}

// stores/ui-store.ts
interface UiState {
  theme: 'light' | 'dark' | 'system';
  sidebarOpen: boolean;
  toggleSidebar: () => void;
  setTheme: (theme: 'light' | 'dark' | 'system') => void;
}
```

### 6.3 研究发起流程

```typescript
// hooks/useResearchQuery.ts
function useStartResearch() {
  return useMutation({
    mutationFn: async (input: ResearchInput) => {
      const res = await fetch('/api/research', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
        body: JSON.stringify({
          query: input.query,
          userId: input.userId,
          tenantId: input.tenantId,
          deepResearch: input.mode === 'deep',
        }),
      });
      if (!res.ok) throw new ApiError(await res.json());
      return res.json() as Promise<ResearchResponse>;
    },
    onSuccess: (data) => {
      // 1. 立即重定向到研究详情页
      router.push(`/research/${data.sessionId}`);
      // 2. SSE 连接由研究详情页的 useResearchSse 自动建立
    },
  });
}
```

---

## 7. SSE 实时通信设计

### 7.1 SSE 客户端封装

```typescript
// lib/sse.ts
import { fetchEventSource } from '@microsoft/fetch-event-source';

interface SseOptions {
  sessionId: string;
  token: string;
  onEvent: (event: ProgressEvent) => void;
  onComplete: () => void;
  onError: (error: Error) => void;
  signal?: AbortSignal;
}

function createSseConnection(options: SseOptions) {
  const { sessionId, token, onEvent, onComplete, onError, signal } = options;

  fetchEventSource(`/api/research/${sessionId}/stream`, {
    headers: { Authorization: `Bearer ${token}` },
    signal,
    onopen: async (response) => {
      if (!response.ok) throw new Error(`SSE 连接失败: ${response.status}`);
    },
    onmessage: (msg) => {
      if (msg.event === 'completed' || msg.event === 'error') {
        const data = JSON.parse(msg.data);
        onEvent(data);
        onComplete();
        return;
      }
      // heartbeat 注释行自动忽略
      const data = JSON.parse(msg.data);
      onEvent(data);
    },
    onerror: (err) => {
      onError(err);
      // 自动重连: 等待 3 秒后重试（最多 5 次）
      throw err; // fetch-event-source 会在 1-30s 后重试
    },
    openWhenHidden: true, // 页面隐藏时也保持连接
  });
}
```

### 7.2 SSE 事件 → UI 映射

| SSE Event (event: 字段) | ProgressEvent.stage | UI 行为 |
|:---|:---|:---|
| `intent_routing` | `INTENT_ROUTING` | Timeline 节点 1 亮起 |
| `planning` | `PLANNING` | Timeline 节点 2 亮起，显示子问题数 |
| `web_searching` | `WEB_SEARCHING` | Timeline 节点 3 Web 分支进度 (x/N) |
| `local_searching` | `LOCAL_SEARCHING` | Timeline 节点 3 Local 分支进度 (x/N) |
| `judging` | `JUDGING` | Timeline 节点 4 亮起，显示去重后数量 |
| `analyzing` | `ANALYZING` | Timeline 节点 5 亮起，显示完备性% |
| `writing` | `WRITING` | Timeline 节点 6 亮起 |
| `completed` | `COMPLETED` | 报告渲染 → Eval 异步加载 |
| `cache_hit` | `CACHE_HIT` | CacheHitBanner 显示，跳过 Timeline |
| `model_fallback` | `MODEL_FALLBACK` | Toast 通知 "模型已降级至 Flash" |
| `search_fallback` | `SEARCH_FALLBACK` | Toast 通知 "搜索已降级" |
| `error` | `ERROR` | 错误信息 + 重试按钮 |

### 7.3 SSE 连接状态指示器

```
● 已连接 (绿色脉冲)    ● 重连中 (黄色旋转)    ● 已断开 (灰色)
```

重连策略：
- 指数退避: 1s → 2s → 4s → 8s → 最大 30s
- 最多重试 5 次，超过后显示"连接断开，点击重试"按钮
- 心跳超时：连续 30 秒无事件视为断连

### 7.4 WorkflowTimeline 组件设计

```
工作流进度

 ○ intent_route ──── ✓ 意图判断完成: research
 │                   (0.3s)
 ○ plan ──────────── ✓ 规划完成: 4 个子问题, 6 个搜索计划
 │                   (2.1s)
 ○ dual_search ───── ◉ 正在检索...
 │  ├─ Web 搜索      ████████░░ 4/6 (3.2s)
 │  └─ Local 搜索    ██████████ 6/6 (2.8s)
 │
 ○ filter ────────── 等待中...
 ○ analyze ───────── 等待中...
 ○ write ─────────── 等待中...
```

---

## 8. API 对接设计

### 8.1 后端 API 完整映射

```typescript
// lib/types.ts — 与后端 DTO 对齐的 TypeScript 类型

// =========================== 请求 ===========================
interface ResearchRequest {
  query: string;          // 1-5000 字符
  userId: string;
  tenantId: string;
  deepResearch: boolean;  // true=深度研究, false=直接回答
}

// =========================== 响应 ===========================
interface ResearchResponse {
  sessionId: string;
  status: 'IN_PROGRESS' | 'COMPLETED' | 'ERROR';
  report?: string;        // 仅 COMPLETED
  error?: string;          // 仅 ERROR
  metadata?: {
    wordCount: number;
    citationCount: number;
    iterationCount: number;
  };
}

// =========================== SSE 事件 ===========================
interface ProgressEvent {
  sessionId: string;
  stage: ResearchStage;
  nodeName: string;       // "plan", "web_search", "write", etc.
  percent: number;         // 0.0 ~ 100.0
  message: string;
  timestamp: string;       // ISO 8601
}

type ResearchStage =
  | 'INTENT_ROUTING'
  | 'PLANNING'
  | 'WEB_SEARCHING'
  | 'LOCAL_SEARCHING'
  | 'JUDGING'
  | 'ANALYZING'
  | 'REFLECTING'
  | 'WRITING'
  | 'COMPLETED'
  | 'CACHE_HIT'
  | 'MODEL_FALLBACK'
  | 'SEARCH_FALLBACK'
  | 'ERROR';

// =========================== 评估 ===========================
interface EvalResult {
  relevance: number;         // 1.0 ~ 5.0
  coherence: number;
  citationAccuracy: number;
  completeness: number;
  conciseness: number;
  overallScore: number;      // 均值
  summary: string;
}

// =========================== 错误 =========================== (RFC 7807)
interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  timestamp?: string;
  errorCode?: string;
  agent?: string;
  errors?: string[];
}
```

### 8.2 API 客户端封装

```typescript
// lib/api.ts
const API_BASE = '/api';  // Next.js rewrite 代理

class ApiClient {
  private token: string;

  constructor(token: string) { this.token = token; }

  private async request<T>(path: string, options?: RequestInit): Promise<T> {
    const res = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${this.token}`,
        ...options?.headers,
      },
    });
    if (!res.ok) {
      const error = await res.json() as ProblemDetail;
      throw new ApiError(res.status, error);
    }
    return res.json() as Promise<T>;
  }

  // POST /api/research
  startResearch(input: ResearchRequest) {
    return this.request<ResearchResponse>('/research', {
      method: 'POST',
      body: JSON.stringify(input),
    });
  }

  // GET /api/research/{sessionId}
  getStatus(sessionId: string) {
    return this.request<ResearchResponse>(`/research/${sessionId}`);
  }
}
```

### 8.3 TanStack Query 集成

```typescript
// hooks/useHistoryList.ts
function useHistoryList(filters: HistoryFilters) {
  return useInfiniteQuery({
    queryKey: ['history', filters],
    queryFn: ({ pageParam = 0 }) =>
      apiClient.getHistory({ ...filters, page: pageParam, size: 20 }),
    getNextPageParam: (lastPage) =>
      lastPage.hasNext ? lastPage.page + 1 : undefined,
    staleTime: 30_000,       // 30 秒内不重新请求
    refetchOnWindowFocus: false,
  });
}
```

---

## 9. 页面详细设计

### 9.1 首页 (ResearchInput)

**组件层次**:
```
ResearchInput (Card)
├── Textarea (自动高度，max 5000 字符)
├── ModeToggle (深度研究 / 直接回答)
├── CharCount + SubmitButton
└── ExampleQueries (3-5 条示例)
```

**交互流程**:
1. 用户输入查询 → 实时字符计数
2. 选择研究模式（默认"深度研究"）
3. 点击"开始研究"→ POST /api/research
4. → 202 Accepted → 重定向到 `/research/{sessionId}`
5. 错误处理：400 (注入检测) → 红色错误提示；500 → 重试按钮

**示例查询**（可配置）:
```typescript
const EXAMPLE_QUERIES = [
  "2026年中国新能源汽车市场趋势与竞争格局分析",
  "全球AI芯片产业链格局及国产替代进展",
  "光伏产业N型电池技术路线对比与未来展望",
  "具身智能人形机器人商业化落地前景分析",
  "中国创新药出海策略与全球监管对比",
];
```

### 9.2 研究详情页 (Research Detail)

**组件层次**:
```
ResearchPage
├── Sidebar (研究上下文)
│   ├── QueryDisplay (原始查询)
│   ├── SubQuestions (子问题列表)
│   ├── SessionMeta (sessionId, 耗时, 状态)
│   ├── EvalSection (完成后显示评分)
│   │   ├── EvalScoreCard (总体评分 + 星级)
│   │   └── EvalRadarChart (五维雷达图)
│   └── ReportOutline (大纲跳转导航)
└── MainContent
    ├── SseStatusBadge (连接状态)
    ├── CacheHitBanner (缓存命中时显示)
    ├── WorkflowTimeline (7 阶段进度)
    └── ReportViewer
        ├── TabBar: [完整报告 | 关键发现 | 引用列表]
        ├── MarkdownRenderer (react-markdown)
        ├── CitationPopover (悬停引用 → 来源浮窗)
        └── EvidenceDrawer (右滑面板: 所有证据详情)
```

**状态机**:
```
IDLE → CONNECTING_SSE → IN_PROGRESS (各阶段事件)
                                    ├──→ CACHE_HIT → COMPLETED
                                    └──→ COMPLETED (normal flow)
                                    └──→ ERROR (any stage)
```

**Skeleton Loading**: 每个节点对应的 UI 区域在数据到达前显示 Skeleton（脉冲灰色占位）。

### 9.3 研究历史页 (History)

**功能**:
- 分页表格/卡片列表（默认按创建时间倒序）
- 搜索：全文搜索 query 字段
- 筛选：按状态 (COMPLETED/ERROR)、按日期范围、按评分区间
- 排序：日期/字数/评分
- 点击行 → 跳转到 `/research/{sessionId}`（查看完整报告）
- 迷你雷达图预览（最后 5 次评分对比）

**组件层次**:
```
HistoryPage
├── HistorySearch (搜索框 + 高级筛选 Dropdown)
│   ├── SearchInput
│   ├── StatusFilter (COMPLETED | ERROR | ALL)
│   ├── DateRangePicker (calendar)
│   └── ScoreFilter (滑动条 0-5)
├── HistoryList (Infinite Scroll or Pagination)
│   └── HistoryCard × N
│       ├── Query preview (100 字符截断)
│       ├── StatusBadge
│       ├── MetaRow (字数 | 引用数 | 日期)
│       ├── MiniRadarChart (Eval 分数)
│       └── ActionButton (查看 | 重新研究)
└── Pagination
```

### 9.4 Prompt 模板管理页 (Admin)

**功能**:
- 查看 8 个 Prompt 模板列表（intent-router, planner, web-scout, local-scout, analyst, writer, direct-answer, eval）
- 点击编辑 → 侧边/全屏编辑器（Monaco Editor 或 CodeMirror）
- 保存 → DB 更新（`UPDATE prompt_templates SET content=...`），1 分钟内自动热生效
- 版本管理：`version` 字段自动递增（乐观锁）
- A/B 测试配置：设置 `ab_group` 为 A/B
- 状态管理：active / inactive / deprecated

**组件层次**:
```
PromptManagementPage
├── PromptTable
│   ├── Column: ID | Name | Version | Status | AB Group | Updated At | Actions
│   └── ActionButton: [编辑] [启用/禁用] [A/B配置]
├── PromptEditor (Dialog / Sheet)
│   ├── MonacoEditor (markdown 语法高亮)
│   ├── DiffModal (与上一版本对比)
│   ├── VariableList (显示可用变量如 {{query}})
│   └── SaveButton + ResetButton
└── AbTestConfig (Dialog)
    ├── RadioGroup: null | A | B
    └── 说明: "A/B 分组用于对比不同 Prompt 版本效果"
```

> **注意**: Prompt 管理页需要独立的后端 API。当前系统仅有读取能力（`DynamicPromptService`），需新增以下后端端点:
> - `GET /api/admin/prompts` — 分页查询所有模板
> - `PUT /api/admin/prompts/{id}` — 更新模板内容
> - `PATCH /api/admin/prompts/{id}/status` — 更新状态/AB分组
> - 这些端点需新增 `@PreAuthorize("hasRole('ADMIN')")` 保护

---

## 10. Markdown 报告渲染

### 10.1 渲染配置

```typescript
// lib/markdown.ts — react-markdown 完整配置
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeRaw from 'rehype-raw';
import rehypeHighlight from 'rehype-highlight';

// 自定义组件映射
const MARKDOWN_COMPONENTS = {
  // 引用 [1] → 点击高亮来源，悬停显示详情 Popover
  a({ href, children }) {
    if (href?.startsWith('#ref-')) {
      const sourceId = href.replace('#ref-', '');
      return <CitationLink sourceId={sourceId}>{children}</CitationLink>;
    }
    return <a href={href} target="_blank" rel="noopener">{children}</a>;
  },
  // 表格 → 响应式滚动
  table({ children }) {
    return <div className="overflow-x-auto"><table>{children}</table></div>;
  },
  // 代码块 → 语言标签 + 复制按钮
  pre({ children }) {
    return <CodeBlock>{children}</CodeBlock>;
  },
};

// 报告视图配置
<ReactMarkdown
  remarkPlugins={[remarkGfm]}
  rehypePlugins={[rehypeRaw, rehypeHighlight]}
  components={MARKDOWN_COMPONENTS}
>
  {reportContent}
</ReactMarkdown>
```

### 10.2 CitationPopover 组件

悬停在报告中的 `[1]` 引用标记上时：
```
┌───────────────────────────────────┐
│ 来源 [WEB01_1-1]                  │
│ ───────────────────────────────── │
│ 标题: 2026年新能源汽车销量突破... │
│ URL:  https://example.com/...     │  ← 可点击跳转
│ 域名: caixin.com (主流媒体)       │
│ 评分: ⭐ 0.72                     │
│ 内容: "2026年上半年新能源汽车..." │  ← 200 字符截断
└───────────────────────────────────┘
```

### 10.3 报告大纲导航

从 Markdown 标题自动提取:
```
📑 报告大纲
├── 1. 市场概况
│   ├── 1.1 全球市场规模
│   └── 1.2 中国市场份额
├── 2. 竞争格局
│   ├── 2.1 头部企业分析
│   └── 2.2 新势力崛起
└── 3. 趋势展望
    ├── 3.1 技术路线
    └── 3.2 政策环境
```

- 点击标题 → 页面平滑滚动到对应章节
- 当前可视章节高亮（IntersectionObserver）

---

## 11. 认证与安全

### 11.1 JWT 认证流程

```
┌──────────┐      ┌──────────┐      ┌──────────┐
│  前端 UI │      │  Auth    │      │  Spring  │
│          │      │  Provider│      │  Backend │
└────┬─────┘      └────┬─────┘      └────┬─────┘
     │  1. 登录请求    │                 │
     │────────────────→│                 │
     │                 │  2. 验证凭据    │
     │                 │────────────────→│
     │                 │  3. JWT Token   │
     │                 │←────────────────│
     │  4. 存储 Token  │                 │
     │←────────────────│                 │
     │                 │                 │
     │  5. API 请求 (Authorization: Bearer <token>)
     │─────────────────────────────────────→│
     │                                      │
     │  6. Token 过期 → 401                 │
     │←─────────────────────────────────────│
     │  7. 刷新 Token                       │
     │─────────────────────────────────────→│
```

### 11.2 Token 管理

```typescript
// lib/jwt.ts
class JwtManager {
  static getToken(): string | null {
    return localStorage.getItem('access_token');
  }

  static setToken(token: string) {
    localStorage.setItem('access_token', token);
  }

  static decodeToken(token: string): JwtPayload {
    // JWT payload 解析（不验证签名，仅提取 userId/tenantId/roles）
    const payload = token.split('.')[1];
    return JSON.parse(atob(payload));
  }

  static isExpired(token: string): boolean {
    const { exp } = this.decodeToken(token);
    return Date.now() >= exp * 1000;
  }
}
```

### 11.3 安全措施

| 措施 | 实现 |
|:---|:---|
| **Token 存储** | `httpOnly` Cookie (via BFF) 或 `localStorage` + XSS 防护 (CSP) |
| **请求拦截** | 所有 `/api/*` 请求自动附加 `Authorization: Bearer` |
| **401 处理** | 自动重定向到登录页；刷新 Token 失败则清除本地状态 |
| **CSP** | `Content-Security-Policy` 限制脚本来源 |
| **前端注入防护** | 输入校验 (zod schema) + 输出转义 (React 默认) |

---

## 12. 主题与国际化

### 12.1 主题配置

```typescript
// tailwind.config.ts — shadcn/ui CSS Variables
theme: {
  extend: {
    colors: {
      // 研究流程节点颜色
      workflow: {
        intent:    'hsl(220, 90%, 56%)',   // 蓝
        plan:      'hsl(280, 65%, 50%)',   // 紫
        search:    'hsl(170, 80%, 40%)',   // 青
        filter:    'hsl(45, 90%, 50%)',    // 黄
        analyze:   'hsl(20, 90%, 50%)',    // 橙
        write:     'hsl(350, 75%, 50%)',   // 红
        completed: 'hsl(140, 50%, 45%)',   // 绿
        error:     'hsl(0, 85%, 55%)',     // 深红
      }
    }
  }
}
```

### 12.2 Dark Mode

- next-themes 实现
- 切换按钮放在 Navbar 右侧
- 支持 `light` | `dark` | `system` 三种模式
- CSS Variables 自动切换（shadcn/ui 内置支持）

### 12.3 国际化（初期中文优先，预留英文扩展）

```typescript
// lib/i18n.ts (预留)
// 初期仅支持 zh-CN，使用硬编码中文文案
// 后续扩展: next-intl 或 react-i18next
```

---

## 13. 性能优化策略

### 13.1 代码分割

```typescript
// Next.js Dynamic Import
const ReportViewer = dynamic(() => import('@/components/research/ReportViewer'), {
  loading: () => <ReportSkeleton />,
  ssr: false,  // Markdown 渲染仅客户端
});

const EvalRadarChart = dynamic(() => import('@/components/research/EvalRadarChart'), {
  ssr: false,  // Recharts 仅客户端
});
```

### 13.2 具体优化

| 优化项 | 策略 |
|:---|:---|
| **Bundle 大小** | 路由级代码分割；shadcn/ui 按需引入（只打包使用的组件） |
| **图片** | Next.js `<Image>` 自动优化；Logo SVG inline |
| **SSE 背压** | `events` 数组限制 200 条；超出时丢弃最旧的非关键事件 |
| **报告渲染** | 超长报告 (>10000 字) 使用虚拟滚动分段渲染 |
| **缓存策略** | 研究历史列表 TanStack Query 缓存 30s；报告内容缓存 5min |
| **Prefetch** | 鼠标悬停历史卡片时 prefetch 报告数据 |
| **Font** | 系统字体栈，无需加载 Web Fonts |

### 13.3 移动端适配断点

```typescript
// Tailwind 响应式断点
// sm: 640px   — 手机横屏
// md: 768px   — 平板
// lg: 1024px  — 桌面
// xl: 1280px  — 大屏
// 2xl: 1536px — 超大屏

// 关键适配:
// - 手机: 单列布局，Sidebar → BottomSheet
// - 平板: 双列布局，Sidebar 可折叠
// - 桌面: 三列布局，Sidebar 固定 + 大纲侧边栏
```

---

## 14. 测试策略

### 14.1 测试金字塔

```
        ┌────────┐
        │  E2E   │  Playwright — 关键用户流程
        │  10%   │
       ┌┴────────┴┐
       │ 集成测试 │  Vitest — API mock + 组件交互
       │   30%    │
      ┌┴──────────┴┐
      │  单元测试  │  Vitest — Hooks, Utils, 状态逻辑
      │    60%     │
      └────────────┘
```

### 14.2 测试覆盖重点

| 层级 | 测试内容 |
|:---|:---|
| **单元测试** | `useResearchSse` SSE 事件解析；`jwt.ts` Token 解析/过期判断；Zustand store 状态转换；数据类型转换函数 |
| **集成测试** | `ResearchInput` 提交 → API mock → 重定向；`WorkflowTimeline` 接收不同事件序列的渲染；`ReportViewer` 正确渲染各种 Markdown 语法 |
| **E2E** | 完整研究流程（输入→提交→SSE进度→报告查看）；历史列表分页/搜索/筛选；Prompt 编辑→保存；Dark mode 切换 |

### 14.3 SSE Mock

```typescript
// test/mocks/sse.ts
function mockSseEvents(events: ProgressEvent[]) {
  // 使用 MSW (Mock Service Worker) 模拟 SSE 流
  const encoder = new TextEncoder();
  const stream = new ReadableStream({
    start(controller) {
      for (const event of events) {
        const data = `event: ${event.stage.toLowerCase()}\ndata: ${JSON.stringify(event)}\n\n`;
        controller.enqueue(encoder.encode(data));
      }
      controller.close();
    },
  });
  return new Response(stream, {
    headers: { 'Content-Type': 'text/event-stream' },
  });
}
```

---

## 15. 实施计划

### 15.1 Phase 划分

#### Phase 1: 基础框架搭建 (2-3 天)

| 任务 | 产出 |
|:---|:---|
| 项目初始化 (Next.js + TS + Tailwind + shadcn/ui) | `ui/` 目录结构完成 |
| 基础布局组件 (Navbar, Sidebar, Footer) | 布局骨架就绪 |
| 路由框架 (4 个页面 + Loading/Error 状态) | 页面可导航 |
| 主题系统 (Light/Dark) | 主题可切换 |
| 类型定义 (`lib/types.ts`) | 与后端 DTO 对齐 |
| API 客户端 + JWT 管理 | Token 注入就绪 |
| Zustand stores (auth, sse, ui) | 状态管理就绪 |

#### Phase 2: 核心研究流程 (3-4 天)

| 任务 | 产出 |
|:---|:---|
| `ResearchInput` 组件 (首页) | 可发起研究 |
| SSE 客户端 (`lib/sse.ts`) | 接收进度事件 |
| `useResearchSse` Hook | 事件状态管理 |
| `WorkflowTimeline` 组件 | 7 阶段进度可视化 |
| `SseStatusBadge` 组件 | 连接状态指示 |
| 研究详情页 | 完整 SSE → 进度展示 |
| 错误状态 + 重试逻辑 | 容错就绪 |
| `CacheHitBanner` 组件 | 缓存命中展示 |

#### Phase 3: 报告渲染 (2-3 天)

| 任务 | 产出 |
|:---|:---|
| Markdown 渲染器配置 (react-markdown + 插件) | GFM 完整支持 |
| `ReportViewer` 组件 | 报告 Tab 切换 |
| `CitationPopover` 组件 | 引用悬停浮窗 |
| `EvidenceDrawer` 组件 | 证据面板 |
| `ReportOutline` 组件 | 大纲导航 |
| 代码块语法高亮 + 复制按钮 | 排版优化 |
| 响应式报告排版 | 移动端适配 |

#### Phase 4: 评估展示 + 历史 (2-3 天)

| 任务 | 产出 |
|:---|:---|
| `EvalScoreCard` + `EvalRadarChart` 组件 | 评分可视化 |
| 研究历史页 (列表 + 搜索 + 筛选) | 历史浏览 |
| `HistoryCard` 组件 (含迷你雷达图) | 历史卡片 |
| TanStack Query 集成 (分页/缓存) | 数据层就绪 |
| Sidebar 研究上下文面板 | 侧边栏完善 |

#### Phase 5: 管理后台 + 打磨 (2-3 天)

| 任务 | 产出 |
|:---|:---|
| Prompt 管理页 (列表 + 编辑器) | Admin 页面 |
| `PromptEditor` 组件 (Monaco Editor) | 代码编辑器 |
| 后端 API 补充 (Prompt CRUD) | 管理接口 |
| 动画/过渡效果 (framer-motion) | 微交互 |
| 响应式适配完善 | 全端支持 |
| Toast 通知系统 (sonner) | 用户提示 |
| 性能优化 + Bundle 分析 | 性能达标 |

#### Phase 6: 测试 + 文档 (2-3 天)

| 任务 | 产出 |
|:---|:---|
| 单元测试 (Hooks + Utils + Stores) | 覆盖 60%+ |
| 组件集成测试 | 覆盖 30%+ |
| E2E 测试 (关键流程) | 覆盖 10%+ |
| Storybook (可选) | 组件文档 |
| README.md | 前端开发指南 |

### 15.2 总计: 13-19 天 (1 人全职)

---

## 附录 A: 后端 API 补充建议

前端开发过程中，建议同步补充以下后端 API 以支持管理功能：

### A.1 Prompt 管理 API

```
GET    /api/admin/prompts              → List<PromptTemplateEntity>
GET    /api/admin/prompts/{id}         → PromptTemplateEntity
PUT    /api/admin/prompts/{id}         → 更新 content/status/abGroup
POST   /api/admin/prompts/{id}/reset   → 重置为 classpath 默认值
```

### A.2 研究历史 API

```
GET    /api/history                    → Page<ResearchHistory>
GET    /api/history/{sessionId}        → ResearchHistory (含完整 report)
DELETE /api/history/{sessionId}        → 删除研究记录
POST   /api/history/{sessionId}/re-run → 重新执行相同查询
```

### A.3 用户画像 API

```
GET    /api/user/profile               → UserProfile
PUT    /api/user/preferences           → 更新偏好设置
```

---

## 附录 B: 前端环境变量

```bash
# ui/.env.example
BACKEND_URL=http://localhost:8080           # Spring Boot 后端地址
NEXT_PUBLIC_APP_NAME=DeepResearch           # 应用名称
NEXT_PUBLIC_DEFAULT_TENANT=default          # 默认租户
NEXT_PUBLIC_MAX_QUERY_LENGTH=5000           # 最大查询长度
NEXT_PUBLIC_SSE_RECONNECT_MAX=5             # SSE 最大重连次数
NEXT_PUBLIC_SSE_HEARTBEAT_TIMEOUT=30000     # SSE 心跳超时 (ms)
```

---

## 附录 C: 技术依赖清单

```json
{
  "dependencies": {
    "next": "^15.0.0",
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "@microsoft/fetch-event-source": "^2.0.1",
    "@tanstack/react-query": "^5.0.0",
    "@tanstack/react-table": "^8.0.0",
    "zustand": "^5.0.0",
    "react-markdown": "^9.0.0",
    "remark-gfm": "^4.0.0",
    "rehype-raw": "^7.0.0",
    "rehype-highlight": "^7.0.0",
    "recharts": "^2.0.0",
    "framer-motion": "^11.0.0",
    "sonner": "^1.0.0",
    "react-hook-form": "^7.0.0",
    "@hookform/resolvers": "^3.0.0",
    "zod": "^3.0.0",
    "dayjs": "^1.11.0",
    "next-themes": "^0.3.0",
    "lucide-react": "^0.400.0",
    "clsx": "^2.0.0",
    "tailwind-merge": "^2.0.0"
  },
  "devDependencies": {
    "typescript": "^5.0.0",
    "@types/react": "^19.0.0",
    "@types/node": "^22.0.0",
    "vitest": "^2.0.0",
    "@testing-library/react": "^16.0.0",
    "@testing-library/jest-dom": "^6.0.0",
    "playwright": "^1.0.0",
    "eslint": "^9.0.0",
    "prettier": "^3.0.0",
    "husky": "^9.0.0",
    "lint-staged": "^15.0.0"
  }
}
```

---

> 📌 **设计总结**: 本方案构建一个基于 Next.js 15 + shadcn/ui + TanStack Query 的现代化前端应用，通过 SSE 实现工作流进度的实时可视化，提供完整的 Markdown 研报渲染、评估分数展示、研究历史管理等功能。技术选型遵循 2026 年 React 生态最佳实践，状态管理分层清晰，错误处理和性能优化策略完备。
