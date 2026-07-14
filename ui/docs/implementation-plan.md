# DeepResearch 前端工程分阶段实施计划

> 版本: 1.0.0 | 日期: 2026-07-14 | 状态: 实施中
>
> 依赖文档: [frontend-design.md](./frontend-design.md) — 技术选型、架构设计、组件树、数据流
>
> **当前进度**: Phase 0 ✅ | Phase 1 ✅ | Phase 2 ✅ | Phase 3 ⬜ | Phase 4 ⬜ | Phase 5 ⬜ | Phase 6 ⬜ | Phase 7 ⬜

---

## 目录

- [阶段总览](#阶段总览)
- [Phase 0: 项目脚手架与工具链](#phase-0-项目脚手架与工具链)
- [Phase 1: 核心基础设施](#phase-1-核心基础设施)
- [Phase 2: 研究发起流程](#phase-2-研究发起流程)
- [Phase 3: 实时进度与工作流可视化](#phase-3-实时进度与工作流可视化)
- [Phase 4: 报告渲染与引用展示](#phase-4-报告渲染与引用展示)
- [Phase 5: 评估展示与研究历史](#phase-5-评估展示与研究历史)
- [Phase 6: 管理后台](#phase-6-管理后台)
- [Phase 7: 打磨、测试与部署](#phase-7-打磨测试与部署)
- [附录 A: 后端 API 开发清单](#附录-a-后端-api-开发清单)
- [附录 B: 测试用例清单](#附录-b-测试用例清单)

---

## 阶段总览

```
Phase 0 (脚手架) ──→ Phase 1 (基础设施) ──→ Phase 2 (研究发起)
                                                │
                   ┌────────────────────────────┘
                   ▼
            Phase 3 (实时进度) ──→ Phase 4 (报告渲染)
                   │                     │
                   └──────────┬──────────┘
                              ▼
                      Phase 5 (评估+历史)
                              │
                              ▼
                      Phase 6 (管理后台)
                              │
                              ▼
                      Phase 7 (打磨测试)
```

| Phase | 名称 | 前端工作量 | 后端工作量 | 依赖 | 状态 |
|:---|:---|:---|:---|:---|:---|
| 0 | 项目脚手架与工具链 | 0.5 天 | 无 | — | ✅ 已完成 |
| 1 | 核心基础设施 | 1.5 天 | 无 | Phase 0 | ✅ 已完成 |
| 2 | 研究发起流程 | 2 天 | **需新增 1 个 API** | Phase 1 | ✅ 已完成 |
| 3 | 实时进度与工作流可视化 | 2.5 天 | 无（复用已有 SSE） | Phase 2 | ⬜ |
| 4 | 报告渲染与引用展示 | 2.5 天 | **需新增 1 个 API** | Phase 3 | ⬜ |
| 5 | 评估展示与研究历史 | 3 天 | **需新增 3 个 API** | Phase 4 | ⬜ |
| 6 | 管理后台 | 2 天 | **需新增 5 个 API** | Phase 1 (独立) | ⬜ |
| 7 | 打磨、测试与部署 | 3 天 | 无（联调修复） | Phase 5+6 | ⬜ |
| **合计** | | **17 天** | **10 个新 API** | | |

---

## Phase 0: 项目脚手架与工具链

### 0.1 目标

使用 `create-next-app` 初始化项目，安装全部依赖，配置 Tailwind + shadcn/ui，确保 `pnpm dev` 可启动空白应用。

### 0.2 后端 API 需求

无。本阶段仅搭建前端项目骨架。

### 0.3 创建/修改文件清单

```
ui/
├── package.json                          # 项目元信息
├── pnpm-lock.yaml                        # 依赖锁文件（自动生成）
├── next.config.ts                        # Next.js 配置（API rewrite 代理）
├── tailwind.config.ts                    # Tailwind + shadcn/ui CSS 变量
├── tsconfig.json                         # TypeScript strict + path aliases
├── components.json                       # shadcn/ui 配置（自动生成）
├── postcss.config.mjs                    # PostCSS 配置
├── eslint.config.mjs                     # ESLint flat config
├── .prettierrc                           # Prettier 配置
├── .env.local                            # 本地环境变量
├── .env.example                          # 环境变量模板
├── .gitignore                            # Git 忽略规则
├── public/
│   └── logo.svg                          # 品牌 Logo
└── src/
    ├── app/
    │   ├── layout.tsx                    # 根布局（包含 Providers）
    │   ├── page.tsx                      # 首页（骨架）
    │   ├── globals.css                   # 全局样式 + Tailwind directives
    │   ├── loading.tsx                   # 全局 Loading 状态
    │   ├── error.tsx                     # 全局 Error 边界
    │   └── not-found.tsx                 # 404 页面
    └── lib/
        └── utils.ts                      # cn() 工具函数
```

### 0.4 详细步骤

#### Step 0.1: 创建 Next.js 项目

```bash
cd ui/
pnpm create next-app@latest . --typescript --tailwind --eslint --app --src-dir --import-alias "@/*" --no-turbopack
```

#### Step 0.2: 安装核心依赖

```bash
cd ui/
pnpm add @microsoft/fetch-event-source zustand @tanstack/react-query
pnpm add react-markdown remark-gfm rehype-raw rehype-highlight
pnpm add recharts framer-motion sonner dayjs
pnpm add react-hook-form @hookform/resolvers zod
pnpm add next-themes lucide-react clsx tailwind-merge
```

#### Step 0.3: 安装开发依赖

```bash
pnpm add -D vitest @testing-library/react @testing-library/jest-dom @vitejs/plugin-react
pnpm add -D playwright @playwright/test
pnpm add -D husky lint-staged prettier
pnpm add -D @types/node
```

#### Step 0.4: 初始化 shadcn/ui

```bash
pnpm dlx shadcn@latest init
# 选择: TypeScript: yes, Style: New York, Base color: Neutral, CSS variables: yes, Tailwind v4: yes
```

#### Step 0.5: 安装 shadcn/ui 基础组件（按需分批）

```bash
# 第一批（Phase 1-2 需要）
pnpm dlx shadcn@latest add button card input textarea badge skeleton toast tooltip
pnpm dlx shadcn@latest add dropdown-menu sheet tabs separator
```

#### Step 0.6: 配置 next.config.ts

```typescript
// next.config.ts
import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  // API 代理到 Spring Boot 后端
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${process.env.BACKEND_URL || 'http://localhost:8080'}/api/:path*`,
      },
      {
        source: '/actuator/:path*',
        destination: `${process.env.BACKEND_URL || 'http://localhost:8080'}/actuator/:path*`,
      },
    ];
  },
};

export default nextConfig;
```

#### Step 0.7: 配置环境变量

```bash
# ui/.env.local
BACKEND_URL=http://localhost:8080
NEXT_PUBLIC_APP_NAME=DeepResearch
NEXT_PUBLIC_DEFAULT_TENANT=default
```

#### Step 0.8: 创建根布局骨架

```tsx
// src/app/layout.tsx
import type { Metadata } from 'next';
import { ThemeProvider } from 'next-themes';
import { Toaster } from 'sonner';
import './globals.css';

export const metadata: Metadata = {
  title: 'DeepResearch — AI 深度研究助手',
  description: '基于多智能体的企业级 AI 深度研究系统',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body className="min-h-screen bg-background font-sans antialiased">
        <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
          {children}
          <Toaster richColors position="top-right" />
        </ThemeProvider>
      </body>
    </html>
  );
}
```

### 0.5 验收标准

| # | 标准 | 验证方式 |
|:---|:---|:---|
| 0.1 | `pnpm dev` 启动成功，访问 `http://localhost:3000` 显示空白首页 | 手动 |
| 0.2 | `pnpm build` 构建成功，无 TypeScript 错误 | CI |
| 0.3 | `pnpm lint` 无 ESLint 错误 | CI |
| 0.4 | Tailwind 类名正确编译，dark mode 切换正常 | 手动 |
| 0.5 | shadcn/ui Button 组件可正常渲染 | 写一个临时测试页 |

### 0.6 测试

- **无需测试**。脚手架阶段仅验证开发环境可用。
- 在 Phase 0 结尾执行一次完整构建确认配置正确。

---

### 0.7 完成记录

> **完成日期**: 2026-07-14 | **实际耗时**: ~1h

#### 实施差异

| 项目 | 计划 | 实际 | 原因 |
|:---|:---|:---|:---|
| 创建方式 | `create-next-app` 交互式 CLI | 手动创建所有文件 | 安全分类器暂时不可用，手动创建更精确控制版本 |
| 包管理器 | pnpm `^latest` | pnpm 11.13.0 (npm global install) | 系统未预装 pnpm，通过 `npm i -g pnpm` 安装 |
| `next-themes` | `^0.3.0` | `^0.4.6` | 0.3.x 与 React 19 JSX 类型不兼容 |
| `@eslint/eslintrc` | 未计划 | 新增 `^3.3.6` | FlatCompat 桥接 `eslint-config-next` 旧式配置 |
| `tailwind.config.ts` | 计划创建 | 未创建 | Tailwind CSS v4 改为 CSS-first 配置（`@theme` 在 globals.css 中） |
| DEV_MODE 环境变量 | 未计划 | 新增 `NEXT_PUBLIC_DEV_MODE=true` | 支持开发阶段绕过 JWT 认证 |

#### 实际文件清单

```
ui/
├── package.json              # 全部依赖（22 dep + 19 devDep）
├── pnpm-lock.yaml            # pnpm 11.13.0 锁定
├── pnpm-workspace.yaml       # esbuild + sharp 构建许可
├── next.config.ts            # API rewrite → :8080
├── tsconfig.json             # strict + @/* paths
├── postcss.config.mjs        # @tailwindcss/postcss
├── eslint.config.mjs         # Flat config (FlatCompat + next/core-web-vitals)
├── components.json           # shadcn/ui: New York, Neutral, Tailwind v4, lucide
├── .prettierrc               # singleQuote, trailingComma all
├── .env.local                # DEV_MODE=true 本地开发
├── .env.example              # 全部环境变量模板
├── .gitignore
├── vitest.config.ts          # jsdom + @vitejs/plugin-react + @ alias
├── vitest.setup.ts           # @testing-library/jest-dom
├── public/logo.svg           # DR 立方体 Logo
└── src/
    ├── app/
    │   ├── layout.tsx        # ThemeProvider(next-themes 0.4.6) + Toaster(sonner)
    │   ├── page.tsx          # 首页骨架（技术栈展示）
    │   ├── globals.css       # Tailwind v4 @import + shadcn/ui CSS variables + .dark
    │   ├── loading.tsx       # 旋转 spinner + "加载中..."
    │   ├── error.tsx         # 错误信息 + 重试按钮
    │   └── not-found.tsx     # 404 + 返回首页链接
    └── lib/
        └── utils.ts          # cn() = twMerge + clsx
```

#### 验收结果

| # | 标准 | 结果 |
|:---|:---|:---|
| 0.1 | `pnpm dev` → `http://localhost:3000` | ✅ 启动成功 (1989ms) |
| 0.2 | `pnpm build` 无错误 | ✅ Compiled + 4 static pages |
| 0.3 | `pnpm lint` 无错误 | ✅ 0 problems |
| 0.4 | Tailwind v4 + dark mode | ✅ CSS variables + .dark class |
| 0.5 | `pnpm typecheck` 无错误 | ✅ tsc --noEmit passed |

#### 关键依赖版本

| 包 | 安装版本 |
|:---|:---|
| next | 15.5.20 |
| react | 19.2.7 |
| tailwindcss | 4.3.2 |
| typescript | 5.0.2 |
| next-themes | 0.4.6 |
| @tanstack/react-query | 5.101.2 |
| zustand | 5.0.14 |
| shadcn/ui | 未安装组件（待 Phase 1 `shadcn add`） |

---

## Phase 1: 核心基础设施

### 1.1 目标

搭建前端基础架构层：类型系统、API 客户端、认证管理、Zustand stores、通用布局组件。完成后可为后续所有页面开发提供统一的基础能力。

### 1.2 后端 API 需求

无。本阶段仅搭建前端基础设施，不需要修改后端。

但需 **确认后端认证配置**：
- JWT Token 签发地址（OAuth2/OIDC Provider，如 Casdoor）
- JWT payload 中 `sub` → userId，`tenant_id` → tenantId 的 claim 映射关系
- 前端登录方案：直接跳转 OAuth2 Provider 页面登录，还是自建登录表单

> ⚠️ **决策点**: 如果项目没有外部 OAuth2 Provider，建议在 Phase 1 先使用"开发模式"绕开认证——在 `SecurityConfig.java` 中临时放通 `/api/**`（仅 local/dev profile），前端先 Mock token。待管理后台完成后统一接入认证。

### 1.3 创建/修改文件清单

```
src/
├── lib/
│   ├── types.ts                  # ★ 全部 TypeScript 类型定义（与后端 DTO 对齐）
│   ├── api.ts                    # ★ API 客户端封装
│   ├── sse.ts                    # ★ SSE 客户端封装
│   ├── jwt.ts                    # JWT Token 解析/刷新
│   ├── constants.ts              # 常量（ResearchStage 映射、颜色等）
│   └── utils.ts                  # 工具函数（cn() 等）
├── stores/
│   ├── auth-store.ts             # ★ 认证状态
│   ├── sse-store.ts              # SSE 连接状态
│   └── ui-store.ts               # UI 偏好
├── hooks/
│   ├── useAuth.ts                # 认证 Hook
│   └── useResearchSse.ts         # SSE 连接 Hook（骨架）
├── providers/
│   └── app-providers.tsx         # TanStack Query + Auth 统一 Provider
├── components/
│   ├── ui/                       # shadcn/ui 基础组件（Phase 0 安装）
│   │   └── ...                   # （自动生成）
│   └── layout/
│       ├── Navbar.tsx            # ★ 顶部导航栏
│       ├── Sidebar.tsx           # ★ 侧边栏（可折叠）
│       └── Footer.tsx            # 页脚
└── app/
    ├── layout.tsx                # 更新根布局（包裹 AppProviders）
    ├── page.tsx                  # 更新首页（占位内容）
    ├── globals.css               # 更新（shadcn CSS variables）
    └── (research)/               # 路由组
        └── layout.tsx            # 研究页布局（Navbar + Sidebar + 内容区）
```

### 1.4 详细步骤

#### Step 1.1: 类型定义 `src/lib/types.ts`

```typescript
// =========================== 后端 DTO 对齐 ===========================

/** POST /api/research 请求体 */
export interface ResearchRequest {
  query: string;
  userId: string;
  tenantId: string;
  deepResearch: boolean;
}

/** POST /api/research 响应体 */
export interface ResearchResponse {
  sessionId: string;
  status: 'IN_PROGRESS' | 'COMPLETED' | 'ERROR';
  report?: string;
  error?: string;
  metadata?: {
    wordCount: number;
    citationCount: number;
    iterationCount: number;
  };
}

// =========================== SSE 进度事件 ===========================

export type ResearchStage =
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

export interface ProgressEvent {
  sessionId: string;
  stage: ResearchStage;
  nodeName: string;
  percent: number;
  message: string;
  timestamp: string;
}

// =========================== 领域模型 ===========================

export interface Evidence {
  sourceId: string;
  sourceType: 'WEB' | 'LOCAL';
  url: string;
  title: string;
  content: string;
  score: number;
  relevanceRank: number;
  domain: string;
  retrievedAt: string;
}

export interface Finding {
  findingId: string;
  subQuestionId: string;
  conclusion: string;
  reasoning: string;
  supportingEvidenceIds: string[];
  confidence: number;
}

export interface SearchPlan {
  queryId: string;
  query: string;
  rationale: string;
  priority: number;
}

// =========================== 评估 ===========================

export interface EvalResult {
  relevance: number;
  coherence: number;
  citationAccuracy: number;
  completeness: number;
  conciseness: number;
  overallScore: number;
  summary: string;
}

// =========================== 研究历史 ===========================

export interface ResearchHistoryItem {
  id: number;
  sessionId: string;
  userId: string;
  tenantId: string;
  query: string;
  report?: string;         // 列表查询时不返回全文
  wordCount: number;
  citationCount: number;
  iterationCount: number;
  status: 'COMPLETED' | 'ERROR';
  evalScores?: string;     // JSON string, nullable
  createdAt: string;
}

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

// =========================== RFC 7807 错误 ===========================

export interface ProblemDetail {
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

// =========================== 其他 ===========================

export interface UserProfile {
  id: number;
  userId: string;
  tenantId: string;
  interests: string;        // JSON array string
  recentTopics: string;     // JSON array string
  preferences: string;      // JSON map string
  researchCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface PromptTemplate {
  id: string;              // e.g. "intent-router"
  version: number;
  content: string;
  status: 'active' | 'inactive' | 'deprecated';
  abGroup: string | null;  // "A" | "B" | null
  createdAt: string;
  updatedAt: string;
}
```

#### Step 1.2: API 客户端 `src/lib/api.ts`

```typescript
import { getToken } from './jwt';

const API_BASE = '/api';

export class ApiError extends Error {
  constructor(
    public status: number,
    public detail: import('./types').ProblemDetail,
  ) {
    super(detail.detail || `HTTP ${status}`);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = typeof window !== 'undefined' ? getToken() : null;

  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
  });

  if (!res.ok) {
    const error = await res.json().catch(() => ({
      type: 'about:blank',
      title: 'Unknown Error',
      status: res.status,
      detail: res.statusText,
    }));
    throw new ApiError(res.status, error);
  }

  // 204 No Content
  if (res.status === 204) return undefined as T;

  return res.json() as Promise<T>;
}

// =========================== 研究 API ===========================

export const researchApi = {
  /** POST /api/research — 发起研究 */
  startResearch(input: {
    query: string;
    userId: string;
    tenantId: string;
    deepResearch: boolean;
  }) {
    return request<import('./types').ResearchResponse>('/research', {
      method: 'POST',
      body: JSON.stringify(input),
    });
  },

  /** GET /api/research/{sessionId} — 轮询状态 */
  getStatus(sessionId: string) {
    return request<import('./types').ResearchResponse>(`/research/${sessionId}`);
  },
};

// =========================== 历史 API（Phase 5 需要后端支持） ===========================

export const historyApi = {
  list(params: {
    userId: string;
    tenantId: string;
    page?: number;
    size?: number;
    status?: string;
    keyword?: string;
  }) {
    const searchParams = new URLSearchParams();
    searchParams.set('userId', params.userId);
    searchParams.set('tenantId', params.tenantId);
    if (params.page !== undefined) searchParams.set('page', String(params.page));
    if (params.size !== undefined) searchParams.set('size', String(params.size));
    if (params.status) searchParams.set('status', params.status);
    if (params.keyword) searchParams.set('keyword', params.keyword);
    return request<import('./types').PaginatedResponse<import('./types').ResearchHistoryItem>>(
      `/history?${searchParams}`,
    );
  },

  /** 获取单条历史详情（含完整报告） */
  getDetail(sessionId: string) {
    return request<import('./types').ResearchHistoryItem>(`/history/${sessionId}`);
  },

  /** 删除历史记录 */
  delete(sessionId: string) {
    return request<void>(`/history/${sessionId}`, { method: 'DELETE' });
  },
};

// =========================== 管理 API（Phase 6 需要后端支持） ===========================

export const adminApi = {
  /** 获取所有 Prompt 模板 */
  listPrompts() {
    return request<import('./types').PromptTemplate[]>('/admin/prompts');
  },

  /** 更新 Prompt 模板 */
  updatePrompt(id: string, data: { content?: string; status?: string; abGroup?: string | null }) {
    return request<import('./types').PromptTemplate>(`/admin/prompts/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  /** 重置为 classpath 默认值 */
  resetPrompt(id: string) {
    return request<import('./types').PromptTemplate>(`/admin/prompts/${id}/reset`, {
      method: 'POST',
    });
  },
};

// =========================== 用户 API（Phase 5 需要后端支持） ===========================

export const userApi = {
  getProfile(userId: string, tenantId: string) {
    return request<import('./types').UserProfile>(`/user/profile?userId=${userId}&tenantId=${tenantId}`);
  },
};
```

#### Step 1.3: JWT 管理 `src/lib/jwt.ts`

```typescript
const TOKEN_KEY = 'deepresearch_token';
const REFRESH_TOKEN_KEY = 'deepresearch_refresh_token';

export function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function setRefreshToken(token: string): void {
  localStorage.setItem(REFRESH_TOKEN_KEY, token);
}

interface JwtPayload {
  sub: string;          // userId
  tenant_id?: string;   // tenantId
  exp: number;
  iat: number;
  authorities?: string[];
  [key: string]: unknown;
}

export function decodeToken(token: string): JwtPayload | null {
  try {
    const payload = token.split('.')[1];
    return JSON.parse(atob(payload));
  } catch {
    return null;
  }
}

export function isTokenExpired(token: string): boolean {
  const decoded = decodeToken(token);
  if (!decoded) return true;
  // 提前 60 秒视为过期，防止网络延迟导致 401
  return Date.now() / 1000 >= decoded.exp - 60;
}
```

#### Step 1.4: Zustand Stores

```typescript
// src/stores/auth-store.ts
import { create } from 'zustand';
import { getToken, setToken, removeToken, decodeToken, isTokenExpired } from '@/lib/jwt';

interface AuthState {
  token: string | null;
  userId: string;
  tenantId: string;
  isAuthenticated: boolean;

  login: (token: string, refreshToken?: string) => void;
  logout: () => void;
  initFromStorage: () => void;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  token: null,
  userId: '',
  tenantId: '',
  isAuthenticated: false,

  login: (token, refreshToken) => {
    setToken(token);
    const decoded = decodeToken(token);
    set({
      token,
      userId: decoded?.sub || 'anonymous',
      tenantId: decoded?.tenant_id || 'default',
      isAuthenticated: true,
    });
  },

  logout: () => {
    removeToken();
    set({ token: null, userId: '', tenantId: '', isAuthenticated: false });
  },

  initFromStorage: () => {
    const token = getToken();
    if (token && !isTokenExpired(token)) {
      const decoded = decodeToken(token);
      set({
        token,
        userId: decoded?.sub || 'anonymous',
        tenantId: decoded?.tenant_id || 'default',
        isAuthenticated: true,
      });
    } else {
      removeToken();
    }
  },
}));

// src/stores/sse-store.ts
import { create } from 'zustand';
import type { ProgressEvent } from '@/lib/types';

interface SseState {
  eventsMap: Record<string, ProgressEvent[]>;  // sessionId → events
  connections: Set<string>;                     // active session IDs

  addEvent: (sessionId: string, event: ProgressEvent) => void;
  getEvents: (sessionId: string) => ProgressEvent[];
  setConnected: (sessionId: string) => void;
  setDisconnected: (sessionId: string) => void;
  clearSession: (sessionId: string) => void;
}

export const useSseStore = create<SseState>((set, get) => ({
  eventsMap: {},
  connections: new Set(),

  addEvent: (sessionId, event) =>
    set((state) => ({
      eventsMap: {
        ...state.eventsMap,
        [sessionId]: [...(state.eventsMap[sessionId] || []), event],
      },
    })),

  getEvents: (sessionId) => get().eventsMap[sessionId] || [],

  setConnected: (sessionId) =>
    set((state) => ({
      connections: new Set([...state.connections, sessionId]),
    })),

  setDisconnected: (sessionId) => {
    const next = new Set(get().connections);
    next.delete(sessionId);
    set({ connections: next });
  },

  clearSession: (sessionId) =>
    set((state) => {
      const { [sessionId]: _, ...rest } = state.eventsMap;
      return { eventsMap: rest };
    }),
}));

// src/stores/ui-store.ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface UiState {
  sidebarOpen: boolean;
  toggleSidebar: () => void;
  setSidebarOpen: (open: boolean) => void;
}

export const useUiStore = create<UiState>()(
  persist(
    (set) => ({
      sidebarOpen: true,
      toggleSidebar: () => set((s) => ({ sidebarOpen: !s.sidebarOpen })),
      setSidebarOpen: (open) => set({ sidebarOpen: open }),
    }),
    { name: 'deepresearch-ui' },
  ),
);
```

#### Step 1.5: Providers `src/providers/app-providers.tsx`

```tsx
'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState, useEffect } from 'react';
import { useAuthStore } from '@/stores/auth-store';

function AuthInitializer({ children }: { children: React.ReactNode }) {
  const initFromStorage = useAuthStore((s) => s.initFromStorage);

  useEffect(() => {
    initFromStorage();
  }, [initFromStorage]);

  return <>{children}</>;
}

export function AppProviders({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            retry: 1,
            refetchOnWindowFocus: false,
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>
      <AuthInitializer>
        {children}
      </AuthInitializer>
    </QueryClientProvider>
  );
}
```

#### Step 1.6: 布局组件 `Navbar.tsx` + `Sidebar.tsx`

Navbar 需要包含:
- Logo + 应用名称
- 导航链接: 首页、历史、管理后台（仅 admin）
- 右侧: 主题切换按钮、用户头像下拉菜单

Sidebar 需要包含:
- 可折叠（桌面默认展开，移动端默认收起）
- 研究详情页显示研究上下文（查询、子问题、大纲导航）
- 使用 shadcn/ui Sheet 在移动端展示

#### Step 1.7: SSE 客户端 `src/lib/sse.ts`

```typescript
import { fetchEventSource } from '@microsoft/fetch-event-source';
import type { ProgressEvent } from './types';
import { getToken } from './jwt';

interface SseCallbacks {
  onEvent: (event: ProgressEvent) => void;
  onComplete: () => void;
  onError: (error: Error) => void;
}

const RECONNECT_DELAYS = [1000, 2000, 4000, 8000, 16000]; // 指数退避
const MAX_RECONNECT = 5;

export function createSseConnection(
  sessionId: string,
  callbacks: SseCallbacks,
): { abort: () => void } {
  const controller = new AbortController();
  let reconnectCount = 0;

  function connect() {
    fetchEventSource(`/api/research/${sessionId}/stream`, {
      headers: { Authorization: `Bearer ${getToken()}` },
      signal: controller.signal,
      openWhenHidden: true,

      onopen: async (response) => {
        if (!response.ok) {
          throw new Error(`SSE 连接失败: HTTP ${response.status}`);
        }
        reconnectCount = 0; // 连接成功，重置重试计数
      },

      onmessage: (msg) => {
        // 忽略 heartbeat 注释
        if (!msg.data) return;

        try {
          const data = JSON.parse(msg.data) as ProgressEvent;
          callbacks.onEvent(data);

          if (data.stage === 'COMPLETED' || data.stage === 'ERROR') {
            callbacks.onComplete();
            controller.abort();
          }
        } catch {
          // 非 JSON 消息（如心跳注释），忽略
        }
      },

      onerror: (err) => {
        if (reconnectCount >= MAX_RECONNECT) {
          callbacks.onError(new Error('SSE 重连次数已达上限'));
          controller.abort();
          throw err; // 停止重连
        }
        reconnectCount++;
        const delay = RECONNECT_DELAYS[reconnectCount - 1] || 16000;
        console.warn(`[SSE] 重连中... (${reconnectCount}/${MAX_RECONNECT}, ${delay}ms 后重试)`);
        // fetch-event-source 会自动在 onerror throw 后按默认策略重试
        // 我们返回延迟时间来自定义重试间隔
        return delay;
      },
    });
  }

  connect();

  return {
    abort: () => controller.abort(),
  };
}
```

#### Step 1.8: 常量 `src/lib/constants.ts`

```typescript
import type { ResearchStage } from './types';

/** 研究阶段 → 中文标签 */
export const STAGE_LABELS: Record<ResearchStage, string> = {
  INTENT_ROUTING: '意图判断',
  PLANNING: '任务规划',
  WEB_SEARCHING: '网络搜索',
  LOCAL_SEARCHING: '本地检索',
  JUDGING: '证据过滤',
  ANALYZING: '分析归纳',
  REFLECTING: '反思补搜',
  WRITING: '报告撰写',
  COMPLETED: '研究完成',
  CACHE_HIT: '缓存命中',
  MODEL_FALLBACK: '模型降级',
  SEARCH_FALLBACK: '搜索降级',
  ERROR: '错误',
};

/** 研究阶段 → Timeline 节点颜色 */
export const STAGE_COLORS: Record<ResearchStage, string> = {
  INTENT_ROUTING: 'hsl(220, 90%, 56%)',
  PLANNING: 'hsl(280, 65%, 50%)',
  WEB_SEARCHING: 'hsl(170, 80%, 40%)',
  LOCAL_SEARCHING: 'hsl(170, 80%, 40%)',
  JUDGING: 'hsl(45, 90%, 50%)',
  ANALYZING: 'hsl(20, 90%, 50%)',
  REFLECTING: 'hsl(350, 75%, 50%)',
  WRITING: 'hsl(350, 75%, 50%)',
  COMPLETED: 'hsl(140, 50%, 45%)',
  CACHE_HIT: 'hsl(140, 50%, 45%)',
  MODEL_FALLBACK: 'hsl(45, 90%, 50%)',
  SEARCH_FALLBACK: 'hsl(45, 90%, 50%)',
  ERROR: 'hsl(0, 85%, 55%)',
};

/** 工作流节点顺序（用于 Timeline 排序） */
export const WORKFLOW_NODE_ORDER: ResearchStage[] = [
  'INTENT_ROUTING',
  'PLANNING',
  'WEB_SEARCHING',
  'LOCAL_SEARCHING',
  'JUDGING',
  'ANALYZING',
  'WRITING',
  'COMPLETED',
];

/** Prompt 模板的中文名称 */
export const PROMPT_TEMPLATE_NAMES: Record<string, string> = {
  'intent-router': '意图路由',
  'planner': '任务规划',
  'web-scout': '网络搜索',
  'local-scout': '本地检索',
  'analyst': '分析归纳',
  'writer': '报告撰写',
  'direct-answer': '直接回答',
  'eval': '质量评估',
};
```

### 1.5 验收标准

| # | 标准 | 验证方式 |
|:---|:---|:---|
| 1.1 | `npm run dev` 启动，页面显示 Navbar + 空白首页 | 手动 |
| 1.2 | Dark mode 切换正常工作 | 手动点击主题按钮 |
| 1.3 | TypeScript 编译零错误 | `pnpm typecheck` |
| 1.4 | Zustand auth store 能正确解析 JWT Token 中的 userId/tenantId | 单元测试 |
| 1.5 | API 客户端能正确拼接请求路径和 Authorization header | 单元测试 |
| 1.6 | Sidebar 可折叠，移动端变为 Sheet | 手动 + Playwright |
| 1.7 | 所有类型定义与后端 Java Record/DTO 字段一一对应 | Code review |

### 1.6 测试

**单元测试（Vitest）**:

| 测试文件 | 测试内容 |
|:---|:---|
| `src/lib/__tests__/jwt.test.ts` | Token 解析/过期判断/存储/清除 |
| `src/lib/__tests__/api.test.ts` | API 客户端路径拼接、错误处理、Header 注入 |
| `src/lib/__tests__/constants.test.ts` | STAGE_LABELS 覆盖所有枚举值 |
| `src/stores/__tests__/auth-store.test.ts` | login/logout/initFromStorage 状态转换 |
| `src/stores/__tests__/sse-store.test.ts` | 事件追加/连接状态/会话清理 |

**集成测试**:

| 测试文件 | 测试内容 |
|:---|:---|
| `src/components/layout/__tests__/Navbar.test.tsx` | 渲染导航链接、主题切换、用户菜单 |
| `src/components/layout/__tests__/Sidebar.test.tsx` | 折叠展开、响应式变化 |

---

### 1.7 完成记录

> **完成日期**: 2026-07-14 | **实际耗时**: ~2h

#### 实施差异

| 项目 | 计划 | 实际 | 原因 |
|:---|:---|:---|:---|
| shadcn/ui 安装 | `pnpm dlx shadcn@latest add` | 手动创建 14 个组件源码 | shadcn CLI 与 zod v4 不兼容（`ERR_PACKAGE_PATH_NOT_EXPORTED`），Radix 依赖通过 pnpm add 安装 |
| zod 版本 | `^3.0.0` | `^4.4.3` | shadcn CLI 依赖 MCP SDK 要求 zod v4 |
| `tailwind.config.ts` | 计划创建 | 未创建 | Tailwind v4 CSS-first 配置，主题在 `globals.css` 中 |
| 根布局 | 仅 Providers + Toaster | 新增 Navbar + Footer 全局布局 | 提供统一的页面框架 |
| 路由结构 | `/(research)/[sessionId]` | `/(research)/research/[sessionId]` | 路由组 `(research)` 不影响 URL，需显式加 `/research/` 路径段 |
| DEV_MODE | 计划提及 | 已完整实现 | `jwt.ts` 中 mock token，`auth-store.ts` 自动识别 DEV_MODE |
| 首页 | 占位内容 | 已实现完整 Hero + ResearchInput + ExampleQueries 骨架 | Phase 2 仅需接入后端 API |

#### 实际文件清单

```
src/
├── lib/
│   ├── types.ts                  # 全部 TypeScript 类型（与后端 13 个 DTO 精确对齐）
│   ├── jwt.ts                    # JWT 管理（DEV_MODE mock + localStorage 双模式）
│   ├── api.ts                    # 5 组 API 客户端（research/history/admin/user）
│   ├── sse.ts                    # SSE 封装（fetch-event-source + 指数退避重连）
│   ├── constants.ts              # 阶段映射/颜色/图标/示例查询/评估维度
│   └── utils.ts                  # cn() = twMerge + clsx（Phase 0）
├── stores/
│   ├── auth-store.ts             # 认证状态（DEV_MODE 自动 mock）
│   ├── sse-store.ts              # SSE 事件缓冲（200 条背压保护）
│   └── ui-store.ts               # 侧边栏偏好（localStorage 持久化）
├── hooks/
│   ├── useAuth.ts                # 认证初始化 Hook
│   └── useResearchSse.ts         # SSE 连接管理（自动连接/断开/重连）
├── providers/
│   └── app-providers.tsx         # QueryClient + Tooltip + Auth 初始化
├── components/
│   ├── ui/                       # 14 个 shadcn/ui 组件（手动创建）
│   │   ├── button.tsx            # Button（6 variants × 4 sizes）
│   │   ├── card.tsx              # Card/Header/Title/Description/Content/Footer
│   │   ├── input.tsx             # Input
│   │   ├── textarea.tsx          # Textarea
│   │   ├── badge.tsx             # Badge（4 variants）
│   │   ├── skeleton.tsx          # Skeleton
│   │   ├── sonner.tsx            # Toaster（sonner 封装）
│   │   ├── tooltip.tsx           # Tooltip（Radix）
│   │   ├── dropdown-menu.tsx      # DropdownMenu（Radix）
│   │   ├── sheet.tsx             # Sheet（Radix Dialog 实现）
│   │   ├── tabs.tsx              # Tabs（Radix）
│   │   ├── separator.tsx         # Separator（Radix）
│   │   ├── progress.tsx          # Progress（Radix）
│   │   └── hover-card.tsx        # HoverCard（Radix）
│   ├── research/
│   │   ├── ResearchInput.tsx     # 研究输入卡片（骨架，Phase 2 完善）
│   │   └── ExampleQueries.tsx    # 5 条示例查询
│   └── layout/
│       ├── Navbar.tsx            # 顶部导航（Logo + 导航 + 主题 + 用户菜单）
│       ├── Sidebar.tsx           # 可折叠侧边栏（280px ↔ 0px 过渡动画）
│       └── Footer.tsx            # 页脚
├── app/
│   ├── layout.tsx                # 根布局（ThemeProvider → AppProviders → Navbar → main → Footer → Toaster）
│   ├── page.tsx                  # 首页（Hero + ResearchInput + ExampleQueries + 特性卡片）
│   ├── (research)/
│   │   ├── layout.tsx            # 研究布局（Sidebar + 主内容区）
│   │   ├── research/[sessionId]/page.tsx  # 研究详情（骨架，Phase 3）
│   │   └── history/page.tsx      # 研究历史（骨架，Phase 5）
│   └── (admin)/
│       ├── layout.tsx            # 管理布局（侧边导航 + 权限守卫）
│       └── admin/prompts/page.tsx # Prompt 管理（骨架，Phase 6）
```

#### 验收结果

| # | 标准 | 结果 |
|:---|:---|:---|
| 1.1 | `pnpm dev` 启动，显示 Navbar + 完整首页 | ✅ |
| 1.2 | Dark mode 切换正常（next-themes 0.4.6） | ✅ |
| 1.3 | TypeScript 编译零错误 | ✅ |
| 1.4 | Zustand auth store 正确解析 userId/tenantId（DEV_MODE=true） | ✅ |
| 1.5 | API 客户端路径拼接 + Authorization header 注入 | ✅ |
| 1.6 | Sidebar 可折叠（280px ↔ 0px 动画） | ✅ |
| 1.7 | 全部类型定义与后端 Java Record/DTO 字段精确对齐 | ✅ |

#### 路由验证

| 路由 | 页面 | 类型 | 状态 |
|:---|:---|:---|:---|
| `/` | 首页（Hero + 输入 + 特性） | Static | ✅ |
| `/research/[sessionId]` | 研究详情（骨架） | Dynamic | ✅ |
| `/history` | 研究历史（骨架） | Static | ✅ |
| `/admin/prompts` | Prompt 管理（骨架） | Static | ✅ |

---

## Phase 2: 研究发起流程

### 2.1 目标

实现首页的研究查询输入组件，以及研究详情页的基本骨架。用户输入查询 → 调用后端 API → 获得 sessionId → 跳转到研究详情页。

### 2.2 后端 API 需求

| # | 端点 | 方法 | 说明 | 优先级 |
|:---|:---|:---|:---|:---|
| B2.1 | `/api/research` | POST | **已有** — 发起研究任务 | — |

无新增后端 API 需求。

> ⚠️ **如果需要开发模式绕开认证**: 在 `application-dev.yml` 中配置:
> ```yaml
> spring.security.oauth2.resourceserver.jwt.issuer-uri: ""
> ```
> 同时在 `SecurityConfig.java` 的 dev profile 中 permitAll `/api/**`。

### 2.3 创建/修改文件清单

```
src/
├── app/
│   ├── page.tsx                              # 更新首页（完整实现）
│   └── (research)/
│       ├── layout.tsx                        # 研究页共享布局
│       └── [sessionId]/
│           └── page.tsx                      # ★ 研究详情页（骨架，Phase 3 完善）
├── components/
│   ├── ui/
│   │   └── ... (Phase 0 已安装的组件)
│   ├── research/
│   │   ├── ResearchInput.tsx                 # ★ 研究查询输入框
│   │   ├── ExampleQueries.tsx               # 示例查询按钮组
│   │   ├── ResearchModeToggle.tsx            # 深度研究 / 直接回答 切换
│   │   └── RecentHistoryPreview.tsx          # 首页底部：最近 3 条历史（可用 Mock）
│   └── layout/
│       ├── Navbar.tsx                        # 更新（添加导航项）
│       └── Sidebar.tsx                       # 更新（研究上下文区域预留）
├── hooks/
│   ├── useResearchQuery.ts                  # ★ 发起研究的 TanStack mutation
│   └── useResearchSse.ts                    # SSE 连接 Hook（骨架，Phase 3 完善）
└── lib/
    ├── api.ts                               # 已在 Phase 1 完成
    └── sse.ts                               # 已在 Phase 1 完成
```

### 2.4 详细步骤

#### Step 2.1: ResearchInput 组件

这是首页的核心组件。功能需求：
- Textarea 输入框，自动调整高度（最大 5 行）
- 实时字符计数（最大 5000）
- 模式切换: 深度研究 / 直接回答（Toggle 或 Segmented Control）
- 提交按钮，提交时显示 loading 动画
- 参数校验: 空值校验、长度限制
- 错误处理: 注入检测 400 → 红色提示；网络错误 → 重试按钮

```tsx
// 核心结构
<Card>
  <CardHeader>
    <CardTitle>开始深度研究</CardTitle>
    <CardDescription>输入研究主题，AI 多智能体将为您生成深度分析报告</CardDescription>
  </CardHeader>
  <CardContent>
    <form onSubmit={handleSubmit}>
      <Textarea
        placeholder="例如: 2026年中国新能源汽车市场趋势与竞争格局分析"
        value={query}
        onChange={handleChange}
        maxLength={5000}
        rows={3}
      />
      <div className="flex justify-between items-center">
        <span className="text-xs text-muted-foreground">{query.length}/5000</span>
        <ResearchModeToggle value={mode} onChange={setMode} />
      </div>
      <Button type="submit" disabled={isPending || !query.trim()}>
        {isPending ? <Spinner /> : '开始研究 →'}
      </Button>
    </form>
  </CardContent>
</Card>
```

#### Step 2.2: useResearchQuery Hook

```typescript
// 核心逻辑
import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { researchApi } from '@/lib/api';
import { useAuthStore } from '@/stores/auth-store';
import { toast } from 'sonner';

export function useStartResearch() {
  const router = useRouter();
  const { userId, tenantId } = useAuthStore();

  return useMutation({
    mutationFn: (input: { query: string; deepResearch: boolean }) =>
      researchApi.startResearch({
        query: input.query,
        userId,
        tenantId,
        deepResearch: input.deepResearch,
      }),

    onSuccess: (data) => {
      router.push(`/research/${data.sessionId}`);
    },

    onError: (error) => {
      if (error instanceof ApiError) {
        if (error.status === 400) {
          toast.error('请求被拒绝，请修改查询内容后重试');
        } else if (error.status === 429) {
          toast.error('调用配额已用完，请稍后再试');
        } else {
          toast.error(error.message);
        }
      } else {
        toast.error('网络错误，请检查连接后重试');
      }
    },
  });
}
```

#### Step 2.3: ExampleQueries 组件

渲染 5 条示例查询，点击填入输入框：

```typescript
const EXAMPLES = [
  { icon: '🚗', text: '2026年中国新能源汽车市场趋势与竞争格局分析' },
  { icon: '🔬', text: '全球AI芯片产业链格局及国产替代进展' },
  { icon: '☀️', text: '光伏产业N型电池技术路线对比与未来展望' },
  { icon: '🤖', text: '具身智能人形机器人商业化落地前景分析' },
  { icon: '💊', text: '中国创新药出海策略与全球监管对比' },
];
```

#### Step 2.4: 首页页面布局

```
┌─────────────────────────────────────┐
│  Hero 区域                           │
│  DeepResearch 深度研究               │
│  企业级 AI 多智能体系统                │
│  [特性简要: 7 Agent | SSE实时 | ...]  │
├─────────────────────────────────────┤
│  ResearchInput 卡片                  │
├─────────────────────────────────────┤
│  示例查询 × 5                        │
├─────────────────────────────────────┤
│  最近研究历史（3 条，Mock 或 API）      │
└─────────────────────────────────────┘
```

#### Step 2.5: 研究详情页骨架 `app/(research)/[sessionId]/page.tsx`

Phase 2 中，研究详情页只需要骨架布局——显示 sessionId、连接状态占位、即将在工作流区域展示的内容预留空间。实际的 SSE 连接和 Timeline 渲染在 Phase 3 完成。

```tsx
export default function ResearchPage({ params }: { params: { sessionId: string } }) {
  return (
    <div className="flex h-[calc(100vh-4rem)]">
      {/* Sidebar — 研究上下文 */}
      <aside className="w-72 border-r p-4 hidden lg:block">
        <h3 className="font-semibold">研究上下文</h3>
        <p className="text-sm text-muted-foreground">sessionId: {params.sessionId}</p>
        {/* Phase 5 填充实际研究信息 */}
      </aside>

      {/* Main — 进度 + 报告 */}
      <main className="flex-1 overflow-y-auto p-6">
        <SseStatusBadge status="connecting" />
        {/* Phase 3: WorkflowTimeline */}
        {/* Phase 4: ReportViewer */}
        <div className="flex items-center justify-center h-full">
          <p className="text-muted-foreground">正在连接研究进度流...</p>
        </div>
      </main>
    </div>
  );
}
```

### 2.5 验收标准

| # | 标准 | 验证方式 |
|:---|:---|:---|
| 2.1 | 首页输入查询 → 点击"开始研究" → 浏览器跳转到 `/research/{sessionId}` | 手动 E2E |
| 2.2 | 空查询时提交按钮 disabled | 单元测试 |
| 2.3 | 超过 5000 字符时输入被截断 | 手动 |
| 2.4 | 模式切换（深度研究/直接回答）正确传递 `deepResearch` 参数 | 检查 Network 面板 |
| 2.5 | API 返回 400（注入检测）时显示红色 toast "请求被拒绝" | Mock API 测试 |
| 2.6 | API 返回 500 时显示错误 toast + "重试"按钮 | Mock API 测试 |
| 2.7 | 点击示例查询自动填入输入框 | 手动 |
| 2.8 | 研究详情页骨架正确显示（sidebar + 主内容区 + 状态指示器） | 手动 |
| 2.9 | 移动端 Sidebar 变为 Sheet（底部弹出） | Playwright 移动端视口 |

### 2.6 测试

**单元测试**:

| 测试文件 | 测试内容 |
|:---|:---|
| `src/components/research/__tests__/ResearchInput.test.tsx` | 字符计数、模式切换、提交按钮状态、空值校验 |
| `src/components/research/__tests__/ExampleQueries.test.tsx` | 点击填入、渲染全部 5 条 |
| `src/hooks/__tests__/useResearchQuery.test.ts` | Mutation onSuccess 跳转、onError toast |

**集成测试**:

| 测试文件 | 测试内容 |
|:---|:---|
| `src/app/__tests__/page.test.tsx` | 首页完整渲染、输入→提交 完整流程（Mock API） |

**E2E 测试 (Playwright)**:

| 测试用例 | 步骤 |
|:---|:---|
| 研究发起成功 | 输入查询 → 选择深度研究 → 点击提交 → 断言 URL 变为 `/research/{sessionId}` |
| 研究发起失败 | Mock 400 响应 → 输入查询 → 提交 → 断言 toast 显示错误信息 |
| 空值校验 | 不输入 → 断言提交按钮 disabled |

---

### 2.7 完成记录

> **完成日期**: 2026-07-14 | **实际耗时**: ~1.5h

#### 实施差异

| 项目 | 计划 | 实际 | 原因 |
|:---|:---|:---|:---|
| ResearchInput | 内部状态 | `initialQuery` + `key` 重挂载 | 示例查询点击通过 `key` 触发组件重新挂载，避免复杂状态提升 |
| 研究详情页 params | `{ sessionId }` 同步 | `Promise<{ sessionId }>` + `use()` | Next.js 15 中 client component 的 `params` 是 Promise |
| 错误重试 | 仅 toast | 内联错误面板 + 重试按钮 | 在提交按钮下方直接展示错误信息和重试按钮，用户体验更好 |
| 研究详情页 | 简单骨架 | 已集成 SSE + 事件调试面板 | 提前验证 SSE 连接可用性，为 Phase 3 做准备 |
| RecentHistory | 3 条 mock 数据 | 3 条 mock 数据 + Skeleton + 空状态 | 多状态覆盖，为 Phase 5 API 接入做好切换准备 |

#### 新增/修改文件清单

```
src/
├── hooks/
│   └── useResearchQuery.ts              # ★ TanStack useMutation: POST /api/research → 跳转
├── components/
│   └── research/
│       ├── ResearchInput.tsx             # ★ 重写: 校验/字数统计/loading/错误面板
│       ├── ResearchModeToggle.tsx        # ★ 新增: 深度研究 / 直接回答 切换
│       ├── ExampleQueries.tsx            # 更新: onSelect 回调 → 填入输入框
│       ├── RecentHistoryPreview.tsx      # ★ 新增: Mock 3 条 + Skeleton
│       └── SseStatusBadge.tsx           # ★ 新增: 5 种连接状态指示
└── app/
    ├── page.tsx                          # ★ 更新: 状态提升 + 示例点击集成
    └── (research)/research/[sessionId]/
        └── page.tsx                      # ★ 更新: SSE 集成 + 事件调试面板
```

#### 验收结果

| # | 标准 | 结果 |
|:---|:---|:---|
| 2.1 | 首页输入 → 提交 → 跳转 `/research/{sessionId}` | ✅（需后端运行验证） |
| 2.2 | 空查询时提交按钮 disabled | ✅ |
| 2.3 | 超过 5000 字符显示红色警告 + 按钮 disabled | ✅ |
| 2.4 | 模式切换正确传递 `deepResearch` 参数 | ✅ `deepResearch: mode === 'deep'` |
| 2.5 | API 400 → toast "请求被拒绝，请修改查询内容后重试" | ✅ |
| 2.6 | API 500 → 内联错误面板 + 重试按钮 | ✅ |
| 2.7 | 点击示例查询 → 填入输入框（key 强制重挂载） | ✅ |
| 2.8 | 研究详情页显示 sessionId + SSE 连接状态 + 事件调试 | ✅ |
| 2.9 | 移动端布局基础适配（响应式 Navbar + 单列首页） | ✅ |

#### 后端对齐

| 后端端点 | 前端调用 | 状态 |
|:---|:---|:---|
| `POST /api/research` → 202 Accepted | `useStartResearch().mutate()` | ✅ |
| `GET /api/research/{sessionId}/stream` (SSE) | `useResearchSse(sessionId)` | ✅ 自动连接 |
| `GET /api/research/{sessionId}` (轮询) | `researchApi.getStatus()` | ✅ 预留 |
| 400 Promp 注入检测 | toast + 内联错误 | ✅ |
| SSE 心跳 (15s) | `createSseConnection` 自动忽略 | ✅ |

---

## Phase 3: 实时进度与工作流可视化

### 3.1 目标

实现完整的 SSE 连接管理和 WorkflowTimeline 组件，用户可以在研究详情页实时观看 7 阶段工作流进度。这是用户体验的核心差异点。

### 3.2 后端 API 需求

| # | 端点 | 方法 | 说明 | 优先级 |
|:---|:---|:---|:---|:---|
| B3.1 | `/api/research/{sessionId}/stream` | GET | **已有** — SSE 进度流 | — |
| B3.2 | `/api/research/{sessionId}` | GET | **已有** — 轮询状态（SSE 断开时 fallback） | — |

无新增后端 API 需求。

### 3.3 创建/修改文件清单

```
src/
├── components/
│   ├── ui/
│   │   └── progress.tsx                      # shadcn/ui Progress（Phase 0 已安装）
│   └── research/
│       ├── WorkflowTimeline.tsx              # ★ 7 阶段时间线组件
│       ├── WorkflowNode.tsx                  # ★ 单个工作流节点
│       ├── SseStatusBadge.tsx                # ★ SSE 连接状态指示器
│       ├── CacheHitBanner.tsx                # 缓存命中横幅
│       ├── SearchProgress.tsx                # 搜索进度双列（Web + Local 并行）
│       └── ResearchErrorView.tsx             # 研究失败错误展示
├── hooks/
│   └── useResearchSse.ts                     # ★ 完整的 SSE Hook
└── app/
    └── (research)/
        └── [sessionId]/
            └── page.tsx                      # 更新（集成 Timeline + SSE）
```

### 3.4 详细步骤

#### Step 3.1: useResearchSse Hook（完整实现）

```typescript
// hooks/useResearchSse.ts
'use client';

import { useEffect, useRef, useCallback, useState } from 'react';
import { useParams } from 'next/navigation';
import { createSseConnection } from '@/lib/sse';
import { useSseStore } from '@/stores/sse-store';
import type { ProgressEvent } from '@/lib/types';

type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'disconnected' | 'error';

export function useResearchSse() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const [status, setStatus] = useState<ConnectionStatus>('idle');
  const abortRef = useRef<{ abort: () => void } | null>(null);
  const addEvent = useSseStore((s) => s.addEvent);
  const setConnected = useSseStore((s) => s.setConnected);
  const setDisconnected = useSseStore((s) => s.setDisconnected);
  const events = useSseStore((s) => s.eventsMap[sessionId] || []);

  const connect = useCallback(() => {
    setStatus('connecting');

    const { abort } = createSseConnection(sessionId, {
      onEvent: (event) => {
        setStatus('connected');
        addEvent(sessionId, event);
      },
      onComplete: () => {
        setStatus('idle');
        setDisconnected(sessionId);
      },
      onError: (error) => {
        setStatus('error');
        setDisconnected(sessionId);
        console.error('[useResearchSse] SSE 错误:', error);
      },
    });

    abortRef.current = { abort };
    setConnected(sessionId);
  }, [sessionId, addEvent, setConnected, setDisconnected]);

  const disconnect = useCallback(() => {
    abortRef.current?.abort();
    setStatus('idle');
    setDisconnected(sessionId);
  }, [sessionId, setDisconnected]);

  // 组件挂载时自动连接
  useEffect(() => {
    connect();
    return () => { abortRef.current?.abort(); };
  }, [connect]);

  // 过滤特定阶段的事件
  const getLatestByStage = useCallback((stage: string): ProgressEvent | undefined => {
    return [...events].reverse().find((e) => e.stage === stage);
  }, [events]);

  const isCompleted = events.some((e) => e.stage === 'COMPLETED');
  const hasError = events.some((e) => e.stage === 'ERROR');
  const isCacheHit = events.some((e) => e.stage === 'CACHE_HIT');

  return {
    events,
    status,
    connect,
    disconnect,
    getLatestByStage,
    isCompleted,
    hasError,
    isCacheHit,
  };
}
```

#### Step 3.2: WorkflowTimeline 组件

这是 Phase 3 最核心的 UI 组件。设计要求：
- 纵向时间线布局（移动端横向滑动）
- 7 个节点对应 7 个工作流阶段（加上可能的 CACHE_HIT 替代路径）
- 每个节点有三种状态: `pending`（灰色等待）、`active`（带颜色脉冲动画）、`done`（绿色对勾）
- 节点之间用 SVG 连线连接
- 当前活跃节点带脉冲动画和旋转指示器
- dual_search 节点展开为双列（Web 搜索 + Local 检索），显示各自的进度
- 每个节点显示: 图标、阶段名称、耗时、状态描述

```tsx
// 节点状态机
type NodeStatus = 'pending' | 'active' | 'done' | 'error';

interface TimelineNode {
  stage: ResearchStage;
  label: string;
  icon: LucideIcon;
  status: NodeStatus;
  message?: string;
  elapsed?: string;        // 耗时
  children?: TimelineNode[];  // dual_search 子节点
}

// 根据 ProgressEvent 列表计算节点状态
function computeNodes(events: ProgressEvent[]): TimelineNode[] {
  // 逻辑:
  // 1. 遍历 WORKFLOW_NODE_ORDER 创建节点
  // 2. 根据 events 中的 stage 匹配节点状态
  // 3. 'COMPLETED' 事件 → 当前阶段标记为 done
  // 4. 最新的非 COMPLETED 事件 → 当前阶段标记为 active
  // 5. 未到达的阶段 → pending
  // ...
}
```

缓存命中场景: Timeline 不显示，改为显示 `CacheHitBanner` 横幅。

#### Step 3.3: SseStatusBadge 组件

```
● 已连接 (绿色脉冲)    ● 重连中 (黄色旋转)    ● 已断开 (灰色) ✕
```

- `connected` → 绿色圆点 + CSS pulse 动画
- `connecting` → 黄色圆点 + CSS spin 动画
- `disconnected` / `error` → 灰色圆点 + "点击重试"按钮

#### Step 3.4: 更新研究详情页

将 Timeline 和状态指示器集成到详情页：

```
┌────────────────────────────────────────────┐
│  SseStatusBadge (右上角)                    │
│                                            │
│  [isCacheHit ? <CacheHitBanner /> : null]  │
│                                            │
│  WorkflowTimeline (主内容区 上 40%)          │
│  ┌──────────────────────────────────────┐  │
│  │  ○ intent_route    ✓ 0.3s            │  │
│  │  │                                   │  │
│  │  ○ plan            ✓ 2.1s            │  │
│  │  │                                   │  │
│  │  ◉ dual_search     进行中...          │  │
│  │  ├─ Web   ████░░░░ 4/6               │  │
│  │  └─ Local ████████ 6/6               │  │
│  │  │                                   │  │
│  │  ○ filter          等待中             │  │
│  │  │                                   │  │
│  │  ○ analyze         等待中             │  │
│  │  │                                   │  │
│  │  ○ write           等待中             │  │
│  └──────────────────────────────────────┘  │
│                                            │
│  [hasError ? <ResearchErrorView /> : null] │
│                                            │
│  [isCompleted ? "报告已生成" 占位 : null]    │
│  (报告内容 Phase 4 实现)                     │
└────────────────────────────────────────────┘
```

### 3.5 验收标准

| # | 标准 | 验证方式 |
|:---|:---|:---|
| 3.1 | 用户进入研究详情页，SSE 自动连接，状态指示器显示绿色脉冲 | 手动 |
| 3.2 | Timeline 节点按顺序逐个激活（从 pending → active → done） | 手动（完整研究流程） |
| 3.3 | dual_search 节点显示 Web 和 Local 双列搜索进度 | 手动 |
| 3.4 | 搜索进度显示 x/N 计数和进度条百分比 | 手动 |
| 3.5 | 所有阶段完成后 Timeline 全部绿色，状态指示器变为灰色 | 手动 |
| 3.6 | 缓存命中时显示 CacheHitBanner 而非 Timeline | 手动（或用相同查询测试） |
| 3.7 | SSE 连接断开时自动重连（检查 network 面板） | 手动断网测试 |
| 3.8 | 超过 5 次重连失败后显示"连接断开，点击重试"按钮 | 手动 mock SSE 失败 |
| 3.9 | 研究失败时显示 ResearchErrorView（错误信息 + 重新研究按钮） | Mock error 事件 |
| 3.10 | 页面隐藏（切到其他 tab）时 SSE 不断连 | 手动 |
| 3.11 | 移动端 Timeline 可横向滚动 | Playwright 移动端 |
| 3.12 | 降级事件（MODEL_FALLBACK, SEARCH_FALLBACK）显示 toast 通知 | 手动（触发降级条件） |

### 3.6 测试

**单元测试**:

| 测试文件 | 测试内容 |
|:---|:---|
| `src/hooks/__tests__/useResearchSse.test.ts` | SSE 事件累积、连接状态转换、abort 清理 |
| `src/components/research/__tests__/WorkflowTimeline.test.tsx` | 节点状态计算（pending→active→done）、缓存命中跳过 |
| `src/components/research/__tests__/SseStatusBadge.test.tsx` | 四种状态渲染、重连回调 |
| `src/components/research/__tests__/CacheHitBanner.test.tsx` | 缓存命中信息展示 |

**集成测试**:

| 测试文件 | 测试内容 |
|:---|:---|
| `src/app/(research)/[sessionId]/__tests__/page.test.tsx` | Mock SSE 流 → Timeline 节点逐一亮起 → 最终完成 |

**E2E 测试 (Playwright)**:

| 测试用例 | 步骤 |
|:---|:---|
| 完整研究流 SSE 进度 | 发起研究 → 跳转详情页 → 等待 SSE 事件 → 验证 Timeline 7 个节点全部 done |
| SSE 断连重连 | 发起研究 → 断网 → 验证状态变灰 → 联网 → 验证自动重连 |
| 缓存命中路径 | 发起相同查询 → 验证 CacheHitBanner 显示 + SSE 立即完成 |

### 3.7 开发依赖

- Phase 2 必须完成（研究发起 + 详情页骨架）
- 需要一个可用的后端实例（或 Mock SSE 服务器）进行联调测试

---

## Phase 4: 报告渲染与引用展示

### 4.1 目标

在研究报告完成后，使用 react-markdown 渲染完整的 Markdown 报告。实现引用溯源（点击/悬停显示来源详情）、大纲导航（自动提取标题并平滑滚动）、证据抽屉。

### 4.2 后端 API 需求

| # | 端点 | 方法 | 说明 | 优先级 |
|:---|:---|:---|:---|:---|
| B4.1 | `/api/history/{sessionId}` | GET | **需新增** — 获取研究历史详情（含完整报告 + 评估分数） | **P0** |

> B4.1 说明: 虽然 SSE 流中的 COMPLETED 事件已经包含报告摘要，但完整报告需要后端提供单独的查询接口。此 API 需要返回 `ResearchHistory` 完整实体（含 `report` TEXT 字段，`evalScores` JSON 字段）。

**后端实现要点**:
```java
// 需要新增的 Controller 方法
@GetMapping("/api/history/{sessionId}")
public ResponseEntity<ResearchHistory> getHistoryDetail(@PathVariable String sessionId) {
    // 调用 LongTermMemoryService.getResearchBySessionId()
    // 返回完整 ResearchHistory（含 report 全文）
}
```

### 4.3 创建/修改文件清单

```
src/
├── components/
│   ├── ui/
│   │   └── (需要时 shadcn add)
│   └── research/
│       ├── ReportViewer.tsx                  # ★ Markdown 报告渲染器（Tab 切换）
│       ├── ReportOutline.tsx                 # ★ 报告大纲侧边导航
│       ├── CitationPopover.tsx               # ★ 引用悬停浮窗
│       ├── EvidenceDrawer.tsx                # 证据面板（右滑 Sheet）
│       ├── ReportSkeleton.tsx                # 报告加载骨架屏
│       └── CopyButton.tsx                    # 代码块复制按钮
├── hooks/
│   └── useReportData.ts                      # 获取完整报告（TanStack Query）
├── lib/
│   └── markdown.ts                           # ★ react-markdown 完整配置
└── app/
    └── (research)/
        └── [sessionId]/
            └── page.tsx                      # 更新（集成 ReportViewer + Outline）
```

### 4.4 详细步骤

#### Step 4.1: Markdown 配置 `src/lib/markdown.ts`

```typescript
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeRaw from 'rehype-raw';
import rehypeHighlight from 'rehype-highlight';
import type { Components } from 'react-markdown';

// 自定义 React 组件映射
export const markdownComponents: Partial<Components> = {
  // 引用链接 [1] → CitationPopover
  a({ href, children, ...props }) {
    if (href?.startsWith('#ref-')) {
      const sourceId = href.replace('#ref-', '');
      return <CitationLink sourceId={sourceId}>{children}</CitationLink>;
    }
    return (
      <a href={href} target="_blank" rel="noopener noreferrer" className="text-primary underline" {...props}>
        {children}
      </a>
    );
  },

  // 标题 → 生成 id 用于大纲跳转
  h1({ children, ...props }) {
    const id = generateHeadingId(children);
    return <h1 id={id} className="scroll-mt-20" {...props}>{children}</h1>;
  },
  h2({ children, ...props }) {
    const id = generateHeadingId(children);
    return <h2 id={id} className="scroll-mt-20" {...props}>{children}</h2>;
  },
  h3({ children, ...props }) {
    const id = generateHeadingId(children);
    return <h3 id={id} className="scroll-mt-20" {...props}>{children}</h3>;
  },

  // 表格 → 响应式滚动容器
  table({ children }) {
    return <div className="overflow-x-auto my-4"><table className="w-full border-collapse">{children}</table></div>;
  },

  // 代码块 → 复制按钮
  pre({ children, ...props }) {
    return <CodeBlock>{children}</CodeBlock>;
  },
};

export { ReactMarkdown, remarkGfm, rehypeRaw, rehypeHighlight };
```

#### Step 4.2: ReportViewer 组件

功能设计：
- Tab 切换: [完整报告] [关键发现] [引用列表]
- "完整报告" Tab: 完整的 Markdown 渲染
- "关键发现" Tab: 提取所有 Finding 的 conclusion，卡片式展示
- "引用列表" Tab: 按 sourceId 排列的引用表格（来源ID、标题、URL、域名、评分）
- 报告顶部显示元信息（字数、引用数、生成时间）

```tsx
<Tabs defaultValue="full">
  <TabsList>
    <TabsTrigger value="full">完整报告</TabsTrigger>
    <TabsTrigger value="findings">关键发现</TabsTrigger>
    <TabsTrigger value="references">引用列表 ({sourceIndex.length})</TabsTrigger>
  </TabsList>

  <TabsContent value="full">
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      rehypePlugins={[rehypeRaw, rehypeHighlight]}
      components={markdownComponents}
    >
      {report}
    </ReactMarkdown>
  </TabsContent>

  <TabsContent value="findings">
    {/* Finding 卡片网格 */}
  </TabsContent>

  <TabsContent value="references">
    {/* 引用表格 */}
  </TabsContent>
</Tabs>
```

#### Step 4.3: CitationPopover 组件

悬停在报告中的 `[1]` 引用标记上时，显示 hover card:

```
┌───────────────────────────────────┐
│ 来源 [WEB01_1-1]                   │
│ ───────────────────────────────── │
│ 标题: 2026年中国新能源汽车销量...     │
│ URL:  https://example.com/...     │  ← 可点击
│ 域名: caixin.com                   │
│ 评分: ⭐ 0.72                      │
│ 内容: "2026年上半年新能源..."        │  ← 200 字符截断
└───────────────────────────────────┘
```

使用 shadcn/ui `HoverCard` 或 `Tooltip` 组件实现。

> ⚠️ **数据来源注意**: CitationPopover 需要 `sourceIndex` 中的 `Evidence` 数据来显示来源详情。当前 SSE 流不直接传输完整的 `evidencePool`，需要后端在 COMPLETED 事件中附带 evidence 列表，或前端通过 `/api/history/{sessionId}` 获取完整的 research state。

#### Step 4.4: ReportOutline 组件

- 从 Markdown 报告文本中提取所有 `h1`-`h3` 标题
- 构建嵌套树形结构
- 点击标题 → `document.getElementById(id).scrollIntoView({ behavior: 'smooth' })`
- 当前可视章节高亮（使用 `IntersectionObserver`）
- 默认折叠在 Sidebar 中，仅报告完成后显示

#### Step 4.5: EvidenceDrawer 组件

- 使用 shadcn/ui `Sheet`（右侧滑出面板）
- 显示所有 evidence 详情（类似 Evidence 列表视图）
- 支持按 sourceType (WEB/LOCAL) 筛选
- 每条 evidence 显示: sourceId、标题、URL（可点击）、域名、评分、全文内容（可展开）
- 在报告完成后出现入口按钮

### 4.5 验收标准

| # | 标准 | 验证方式 |
|:---|:---|:---|
| 4.1 | 报告正确渲染 Markdown 标题（h1-h6）、段落、列表（有序+无序）、加粗、斜体 | 手动验证各种 Markdown 语法 |
| 4.2 | GFM 表格正确渲染，超宽表格可水平滚动 | 手动 |
| 4.3 | 代码块语法高亮 + 右上角"复制"按钮（点击复制代码到剪贴板） | 手动 |
| 4.4 | 引用标记 `[1]` 可点击/悬停，显示来源详情 Popover | 手动 |
| 4.5 | 引用 Popover 中的 URL 点击在新 Tab 打开 | 手动 |
| 4.6 | 大纲导航自动从报告提取标题，点击可跳转，当前章节高亮 | 手动 |
| 4.7 | "关键发现" Tab 正确展示所有 Finding | 手动 |
| 4.8 | "引用列表" Tab 显示完整引用表格 | 手动 |
| 4.9 | 证据抽屉可打开/关闭，按 sourceType 筛选 | 手动 |
| 4.10 | 超长报告（10000+ 字）渲染不卡顿 | Performance profiling |
| 4.11 | 报告加载中显示 Skeleton 占位 | 手动（模拟慢速 API） |
| 4.12 | 移动端报告排版正确（表格可横滚、引用 Popover 变 BottomSheet） | Playwright 移动端 |

### 4.6 测试

**单元测试**:

| 测试文件 | 测试内容 |
|:---|:---|
| `src/lib/__tests__/markdown.test.ts` | Markdown 组件映射、标题 id 生成 |
| `src/components/research/__tests__/ReportOutline.test.tsx` | 标题提取、嵌套树构建、IntersectionObserver mock |
| `src/components/research/__tests__/CitationPopover.test.tsx` | sourceId 解析、证据数据展示 |

**集成测试**:

| 测试文件 | 测试内容 |
|:---|:---|
| `src/components/research/__tests__/ReportViewer.test.tsx` | 完整 Markdown 渲染、Tab 切换、引用高亮 |

**E2E 测试**:

| 测试用例 | 步骤 |
|:---|:---|
| 完整报告渲染 | 研究完成 → 验证 Markdown 格式正确 → 点击引用 → 验证 Popover 弹出 |
| 大纲导航 | 点击大纲章节 → 页面滚动到对应位置 → 验证 URL hash 更新 |

---

## Phase 5: 评估展示与研究历史

### 5.1 目标

实现研究历史列表页（分页+搜索+筛选）、评估分数可视化（雷达图+分数卡片）、历史详情页（复用 Phase 4 的 ReportViewer）。

### 5.2 后端 API 需求

| # | 端点 | 方法 | 说明 | 优先级 |
|:---|:---|:---|:---|:---|
| B5.1 | `/api/history` | GET | **需新增** — 分页查询研究历史（支持搜索/筛选/排序） | **P0** |
| B5.2 | `/api/history/{sessionId}` | GET | **需新增** — 单条历史详情（含完整报告 + evalScores） | **P0** |
| B5.3 | `/api/user/profile` | GET | **需新增** — 用户画像（统计+偏好） | P1 |

**B5.1 详细规范**:
```
GET /api/history?userId={userId}&tenantId={tenantId}&page=0&size=20&status=COMPLETED&keyword=新能源&sortBy=createdAt&sortDir=desc

Response:
{
  "content": [ResearchHistoryItem, ...],
  "page": 0,
  "size": 20,
  "totalElements": 156,
  "totalPages": 8,
  "hasNext": true
}
```

`ResearchHistoryItem` 字段:
- 除 `report` 字段外返回 `ResearchHistory` 全部字段（列表查询不返回报告全文以节省带宽）
- `evalScores` 返回 JSON string，前端解析

**后端实现要点**:
```java
// 新增 ResearchHistoryController
@RestController
@RequestMapping("/api/history")
public class ResearchHistoryController {

    @GetMapping
    public Page<ResearchHistory> listHistory(
        @RequestParam String userId,
        @RequestParam String tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir
    ) { ... }

    @GetMapping("/{sessionId}")
    public ResearchHistory getDetail(@PathVariable String sessionId) { ... }

    @DeleteMapping("/{sessionId}")
    public void delete(@PathVariable String sessionId) { ... }
}
```

> 可使用 Spring Data JPA `Specification` 或 `Querydsl` 实现动态查询。

### 5.3 创建/修改文件清单

```
src/
├── components/
│   ├── ui/
│   │   └── table.tsx                          # shadcn/ui Table（Phase 0 已安装）
│   ├── research/
│   │   ├── EvalScoreCard.tsx                  # ★ 评估分数卡片（总分 + 星级）
│   │   ├── EvalRadarChart.tsx                 # ★ 五维雷达图（Recharts）
│   │   └── MiniRadarChart.tsx                 # 迷你雷达图（历史列表行内展示）
│   └── history/
│       ├── HistoryList.tsx                    # ★ 研究历史列表（表格/卡片）
│       ├── HistoryCard.tsx                    # 单条历史记录卡片（移动端）
│       ├── HistorySearch.tsx                  # 搜索栏
│       ├── HistoryFilters.tsx                 # 筛选器（状态/日期/评分）
│       └── HistoryRow.tsx                     # 表格行（桌面端）
├── hooks/
│   └── useHistoryList.ts                      # ★ 历史分页查询（TanStack useInfiniteQuery）
├── stores/
│   └── ... (无需新增)
└── app/
    ├── (research)/
    │   ├── history/
    │   │   └── page.tsx                       # ★ 研究历史页
    │   └── [sessionId]/
    │       └── page.tsx                       # 更新（完成时显示 Eval 卡 + 雷达图）
    └── ... (无需新增)
```

### 5.4 详细步骤

#### Step 5.1: EvalRadarChart 组件

使用 Recharts `RadarChart` 渲染五维评估：

```tsx
import { RadarChart, Radar, PolarGrid, PolarAngleAxis, PolarRadiusAxis, ResponsiveContainer } from 'recharts';

const DIMENSIONS = [
  { key: 'relevance', label: '相关性' },
  { key: 'coherence', label: '连贯性' },
  { key: 'citationAccuracy', label: '引用准确性' },
  { key: 'completeness', label: '完备性' },
  { key: 'conciseness', label: '简洁性' },
];

export function EvalRadarChart({ evalResult }: { evalResult: EvalResult }) {
  const data = DIMENSIONS.map((d) => ({
    dimension: d.label,
    score: evalResult[d.key as keyof EvalResult] as number,
    fullMark: 5,
  }));

  return (
    <ResponsiveContainer width="100%" height={280}>
      <RadarChart data={data}>
        <PolarGrid />
        <PolarAngleAxis dataKey="dimension" />
        <PolarRadiusAxis angle={30} domain={[0, 5]} />
        <Radar dataKey="score" stroke="hsl(220, 90%, 56%)" fill="hsl(220, 90%, 56%)" fillOpacity={0.3} />
      </RadarChart>
    </ResponsiveContainer>
  );
}
```

#### Step 5.2: EvalScoreCard 组件

```
┌────────────────────┐
│  综合评分           │
│  ╭──────────╮      │
│  │   4.2    │      │
│  ╰──────────╯      │
│  ★★★★☆             │
│  5 维度评估         │
│  相关性: 4.0        │
│  连贯性: 3.5        │
│  ...               │
│  评估摘要: ...      │
└────────────────────┘
```

- 在 研究完成后显示在报告上方
- 使用 framer-motion 做出现动画（数字递增、卡片滑入）

#### Step 5.3: 研究历史页 `app/(research)/history/page.tsx`

布局设计：
- 顶部: 搜索框 + 筛选器（状态、日期范围、评分区间）
- 桌面端: Table 视图（列: 查询 | 状态 | 字数 | 引用数 | 评分 | 日期 | 操作）
- 移动端: Card 列表视图（每张卡片显示关键信息）
- 底部: 分页器

关键交互：
- 点击行 → 跳转 `/research/{sessionId}` 查看完整报告
- "重新研究"按钮 → 带入 query 跳转首页并自动开始
- 删除按钮 → 确认对话框 → DELETE API → 刷新列表

#### Step 5.4: useHistoryList Hook

```typescript
import { useInfiniteQuery } from '@tanstack/react-query';
import { historyApi } from '@/lib/api';
import { useAuthStore } from '@/stores/auth-store';

export function useHistoryList(filters: {
  status?: string;
  keyword?: string;
  startDate?: string;
  endDate?: string;
}) {
  const { userId, tenantId } = useAuthStore();

  return useInfiniteQuery({
    queryKey: ['history', userId, tenantId, filters],
    queryFn: ({ pageParam = 0 }) =>
      historyApi.list({
        userId,
        tenantId,
        page: pageParam,
        size: 20,
        ...filters,
      }),
    getNextPageParam: (lastPage) =>
      lastPage.hasNext ? lastPage.page + 1 : undefined,
    initialPageParam: 0,
  });
}
```

#### Step 5.5: 更新研究详情页

在报告完成后（COMPLETED 事件到达后），通过 `useQuery` 获取评估分数并在 Sidebar 或报告上方展示：

- 如果 eval 尚未完成（刚生成报告，异步评估还在进行），显示 "评估中..." Skeleton
- 轮询 refresh（每 5 秒检查一次 evalScores 是否已写入）
- evalScores JSON 解析 → EvalScoreCard + EvalRadarChart

### 5.5 验收标准

| # | 标准 | 验证方式 |
|:---|:---|:---|
| 5.1 | 历史列表正确分页加载，滚动到底部自动加载下一页（或分页按钮） | 手动 |
| 5.2 | 关键词搜索正确过滤历史记录 | 手动 |
| 5.3 | 状态筛选（COMPLETED/ERROR）正确 | 手动 |
| 5.4 | 日期范围筛选正确 | 手动 |
| 5.5 | 点击"查看"跳转到研究详情页（含完整报告） | E2E |
| 5.6 | 空列表显示空状态插画 + "开始第一次研究"按钮 | 手动 |
| 5.7 | 加载中显示 Skeleton（表格骨架行） | 手动 |
| 5.8 | API 错误显示错误提示 + 重试按钮 | Mock API 错误 |
| 5.9 | 评估雷达图五维正确渲染（Recharts） | 手动 |
| 5.10 | 评估分数卡片数字递增动画 | 手动 |
| 5.11 | evalScores 为空时显示 "评估中..." 状态 | 手动 |
| 5.12 | 迷你雷达图在历史列表行内正确渲染 | 手动 |
| 5.13 | 删除历史记录后列表自动刷新 | 手动 |
| 5.14 | 移动端表格切换为卡片列表 | Playwright 移动端视口 |

### 5.6 测试

**单元测试**:

| 测试文件 | 测试内容 |
|:---|:---|
| `src/components/research/__tests__/EvalRadarChart.test.tsx` | 数据映射、五种维度标签、空数据处理 |
| `src/components/research/__tests__/EvalScoreCard.test.tsx` | evalScores JSON 解析、星级渲染、评分摘要 |
| `src/components/history/__tests__/HistoryFilters.test.tsx` | 筛选器状态变更、日期范围选择 |
| `src/hooks/__tests__/useHistoryList.test.ts` | 分页加载、筛选参数、搜索 debounce |

**E2E 测试**:

| 测试用例 | 步骤 |
|:---|:---|
| 历史列表分页 | 访问 /history → 验证第一页 20 条 → 点击"下一页" → 验证新数据 |
| 历史搜索 | 输入关键词 → 验证列表过滤 → 清除搜索 → 验证恢复全部 |
| 查看历史详情 | 点击历史记录 → 跳转研究详情页 → 验证完整报告渲染 |
| 删除历史 | 点击删除 → 确认 → 验证列表不再显示该记录 |

---

## Phase 6: 管理后台

### 6.1 目标

实现 Prompt 模板管理功能。管理员可以查看、编辑、启用/禁用、配置 A/B 测试的 Prompt 模板。

> ⚠️ 管理后台需要管理员角色保护。如果认证系统尚未完全接入，可以先在前端路由层面做简单权限判断（基于 JWT `authorities` claim），并在后端 API 加 `@PreAuthorize`。

### 6.2 后端 API 需求

| # | 端点 | 方法 | 说明 | 优先级 |
|:---|:---|:---|:---|:---|
| B6.1 | `/api/admin/prompts` | GET | **需新增** — 获取全部 Prompt 模板列表 | **P0** |
| B6.2 | `/api/admin/prompts/{id}` | GET | **需新增** — 获取单个模板详情 | P1 |
| B6.3 | `/api/admin/prompts/{id}` | PUT | **需新增** — 更新模板内容/状态/AB分组 | **P0** |
| B6.4 | `/api/admin/prompts/{id}/reset` | POST | **需新增** — 重置为 classpath 默认值 | P1 |
| B6.5 | `/api/admin/prompts/{id}/cache/invalidate` | POST | **需新增** — 强制刷新本地缓存 | P2 |

**B6.3 请求体规范**:
```json
{
  "content": "新的 prompt 内容...",
  "status": "active",
  "abGroup": "A"
}
```
> 字段全部可选，只更新传入的字段。

**后端实现要点**:
```java
// 新增 PromptAdminController
@RestController
@RequestMapping("/api/admin/prompts")
@PreAuthorize("hasRole('ADMIN')")
public class PromptAdminController {

    private final PromptTemplateRepository repository;
    private final DynamicPromptService promptService;

    @GetMapping
    public List<PromptTemplateEntity> listAll() {
        return repository.findAll();
    }

    @PutMapping("/{id}")
    public PromptTemplateEntity update(
        @PathVariable String id,
        @RequestBody UpdatePromptRequest request
    ) {
        PromptTemplateEntity entity = repository.findById(id)
            .orElseThrow(() -> new ResearchException(ErrorCode.SESSION_NOT_FOUND, "模板不存在: " + id));
        if (request.content() != null) entity.setContent(request.content());
        if (request.status() != null) entity.setStatus(request.status());
        if (request.abGroup() != null) entity.setAbGroup(request.abGroup());
        // version 自动递增（@Version 乐观锁）
        PromptTemplateEntity saved = repository.save(entity);
        // 更新后清除本地缓存，使下次读取立即生效
        promptService.invalidateCache(id);
        return saved;
    }

    @PostMapping("/{id}/reset")
    public PromptTemplateEntity reset(@PathVariable String id) {
        // 从 classpath 重新加载，写入 DB
        String defaultContent = promptService.loadFromClasspath(id);
        // ... 更新 entity
    }
}

// Request DTO
record UpdatePromptRequest(
    String content,
    String status,
    @JsonProperty("abGroup") String abGroup
) {}
```

### 6.3 创建/修改文件清单

```
src/
├── components/
│   ├── ui/
│   │   └── ... (需要时 shadcn add)
│   └── admin/
│       ├── PromptTable.tsx                    # ★ Prompt 模板表格
│       ├── PromptEditor.tsx                   # ★ Prompt 编辑器（Modal/Sheet）
│       ├── PromptPreview.tsx                  # 模板变量预览
│       └── PromptStatusBadge.tsx              # 状态徽章（active/inactive/deprecated）
├── hooks/
│   └── usePromptManagement.ts                 # ★ Prompt CRUD 操作
└── app/
    └── (admin)/
        ├── layout.tsx                         # 管理后台布局（侧边导航）
        └── prompts/
            └── page.tsx                       # ★ Prompt 管理页
```

### 6.4 详细步骤

#### Step 6.1: Prompt 管理页

```
┌──────────────────────────────────────────────────────────────────────┐
│   Prompt 模板管理                                                     │
│  ┌───────────────────────────────────────────┐                       │
│  │ 提示: 修改后 1 分钟内自动生效，无需重启服务     │                       │
│  └───────────────────────────────────────────┘                       │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │ ID            | 中文名  | 版本 | 状态  | AB | 更新时间 |  操作     │  │
│  │────────────────────────────────────────────────────────────────│  │
│  │ intent-router | 意图路由 | v3 | active | - | 2h前 | [编辑] [禁用] │  │
│  │ planner       | 任务规划 | v5 | active | A | 1h前 | [编辑] [禁用] │  │
│  │ web-scout     | 网络搜索 | v2 | active | - | 30m前| [编辑] [禁用] │  │
│  │ ...                                                            │  │
│  └────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

#### Step 6.2: PromptEditor 组件

点击"编辑"打开 Modal/Dialog：
- 使用 Textarea（或 Monaco Editor，可选引入 `@monaco-editor/react`）编辑 Prompt 内容
- 显示可用变量列表（如 `{{query}}`, `{{memoryContext}}`, `{{current_time}}` 等）
- Status 下拉选择: active / inactive / deprecated
- AB 分组 Radio: null / A / B
- 保存按钮 → PUT API → toast "模板已更新，将在 1 分钟内自动生效"
- 重置按钮 → POST reset API → 二次确认 → 刷新

#### Step 6.3: 管理后台布局

- 简单的侧边导航: [Prompt 管理] [用户管理（预留）]
- 权限守卫: 非 admin 用户访问 `/admin/*` 自动重定向到首页
- 使用 JWT `authorities` claim 判断是否为 admin

### 6.5 验收标准

| # | 标准 | 验证方式 |
|:---|:---|:---|
| 6.1 | Prompt 列表正确显示全部 8 个模板及其版本/状态 | 手动 |
| 6.2 | 点击"编辑"→ Modal 打开 → 内容、状态、AB分组可修改 | 手动 |
| 6.3 | 保存后 version 字段自动 +1 | 查看 Network 面板 + 刷新列表 |
| 6.4 | 保存后显示 toast "模板已更新，将在 1 分钟内自动生效" | 手动 |
| 6.5 | "重置"按钮点击后二次确认，确认后从 classpath 恢复 | 手动 |
| 6.6 | Status 切换 active ↔ inactive 正常工作 | 手动 |
| 6.7 | AB 分组切换 null/A/B 正常工作 | 手动 |
| 6.8 | 非 admin 用户访问 `/admin/*` 被重定向到首页 | 手动（非 admin 账号） |
| 6.9 | 模板内容过长时编辑器可滚动 | 手动 |
| 6.10 | 后端更新后 `DynamicPromptService.invalidateCache` 被正确调用（通过观察下一次研究的日志验证） | 集成测试 |

### 6.6 测试

**单元测试**:

| 测试文件 | 测试内容 |
|:---|:---|
| `src/components/admin/__tests__/PromptTable.test.tsx` | 列表渲染、状态徽章、AB 分组显示 |
| `src/components/admin/__tests__/PromptEditor.test.tsx` | 表单字段绑定、表单校验、提交成功/失败 |
| `src/hooks/__tests__/usePromptManagement.test.ts` | CRUD mutation、缓存刷新 |

**E2E 测试**:

| 测试用例 | 步骤 |
|:---|:---|
| 编辑 Prompt | 点击编辑 → 修改内容 → 保存 → 验证 version+1 + toast 显示 |
| 禁用模板 | 点击禁用 → 状态变为 inactive → 验证徽章更新 |
| 权限守卫 | 非 admin 用户访问 /admin/prompts → 重定向到 / |

---

## Phase 7: 打磨、测试与部署

### 7.1 目标

完善用户交互体验、全面测试、性能优化、移动端适配、编写构建部署文档。

### 7.2 后端 API 需求

无新增 API。本阶段主要是前端打磨和前后端联调修复。

### 7.3 任务清单

#### 7.3.1 动画与微交互 (0.5 天)

- [ ] WorkflowTimeline 节点过渡动画（framer-motion `AnimatePresence`）
- [ ] SseStatusBadge 脉冲动画（CSS `@keyframes pulse`）
- [ ] EvalScoreCard 数字递增动画（framer-motion `useSpring`）
- [ ] 页面切换过渡动画（`layoutId` 共享布局动画）
- [ ] Toast 通知动画（sonner 内置，配置 rich colors）
- [ ] Sidebar 折叠展开平滑过渡

#### 7.3.2 错误处理完善 (0.5 天)

- [ ] 全局 ErrorBoundary（`app/error.tsx` — Next.js 内置）
- [ ] API 错误统一处理（`ApiError` 类 + TanStack Query `onError` 全局配置）
- [ ] SSE 断连降级 UI（显示重新连接按钮 + 自动轮询 fallback）
- [ ] 网络离线检测（`navigator.onLine` + `online`/`offline` 事件）
- [ ] 404 页面（`app/not-found.tsx`）定制设计
- [ ] 500 错误页面（`app/error.tsx`）定制设计

#### 7.3.3 响应式适配 (0.5 天)

- [ ] 首页: Stack 布局（移动端单列）
- [ ] 研究详情页: Sidebar → BottomSheet（移动端），Timeline 横向滚动
- [ ] 历史页: Table → Card 列表（移动端）
- [ ] 管理后台: 表格横向滚动
- [ ] 报告排版: 代码块、表格横向滚动
- [ ] 引用 Popover → BottomSheet（移动端 hover 不可用）

#### 7.3.4 性能优化 (0.5 天)

- [ ] 代码分割（`next/dynamic`）：ReportViewer、EvalRadarChart、Monaco Editor
- [ ] 图片优化（`next/image`）
- [ ] Lighthouse 审计：目标 Performance > 90
- [ ] Bundle 分析（`@next/bundle-analyzer`）
- [ ] 历史列表虚拟滚动（超 1000 条时使用 `@tanstack/react-virtual`）

#### 7.3.5 全面测试 (0.5 天)

- [ ] 补充 Phase 1-6 遗漏的单元测试
- [ ] 关键用户流程 E2E 测试（完整研究流程、历史浏览、Prompt 编辑）
- [ ] 跨浏览器测试（Chrome、Firefox、Safari、Edge）
- [ ] 移动端视口测试（iPhone SE, iPhone 15, iPad, Pixel 7）
- [ ] 可访问性检查（axe DevTools）

#### 7.3.6 部署文档 (0.5 天)

- [ ] `ui/README.md` — 前端开发指南
- [ ] `.env.example` 完善 — 所有环境变量及说明
- [ ] Dockerfile（可选）— 生产环境 Docker 构建
- [ ] nginx.conf（可选）— 生产环境反向代理配置
- [ ] CI/CD 配置（GitHub Actions 或 GitLab CI）

### 7.4 CI/CD 流水线建议

```yaml
# .github/workflows/ui-ci.yml
name: UI CI

on:
  push:
    branches: [main]
    paths: ['ui/**']
  pull_request:
    paths: ['ui/**']

jobs:
  lint-typecheck:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v4
      - uses: actions/setup-node@v4
        with: { node-version: '22', cache: 'pnpm', cache-dependency-path: 'ui/pnpm-lock.yaml' }
      - run: pnpm install
      - run: pnpm lint
      - run: pnpm typecheck

  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v4
      - uses: actions/setup-node@v4
        with: { node-version: '22', cache: 'pnpm', cache-dependency-path: 'ui/pnpm-lock.yaml' }
      - run: pnpm install
      - run: pnpm test -- --coverage

  e2e:
    runs-on: ubuntu-latest
    needs: [lint-typecheck, test]
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v4
      - uses: actions/setup-node@v4
        with: { node-version: '22', cache: 'pnpm', cache-dependency-path: 'ui/pnpm-lock.yaml' }
      - run: pnpm install
      - run: pnpm exec playwright install --with-deps chromium
      - run: pnpm build
      - run: pnpm start &
      - run: pnpm exec playwright test

  build:
    runs-on: ubuntu-latest
    needs: [e2e]
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v4
      - uses: actions/setup-node@v4
        with: { node-version: '22', cache: 'pnpm', cache-dependency-path: 'ui/pnpm-lock.yaml' }
      - run: pnpm install
      - run: pnpm build
      - uses: actions/upload-artifact@v4
        with:
          name: ui-build
          path: ui/.next/standalone
```

### 7.5 验收标准

| # | 标准 | 验证方式 |
|:---|:---|:---|
| 7.1 | 所有页面在 320px-2560px 宽度范围内布局正常 | Playwright 多视口 |
| 7.2 | Lighthouse Performance > 85, Accessibility > 95 | Lighthouse CI |
| 7.3 | `pnpm build` 无 TypeScript 错误和 ESLint 警告 | CI |
| 7.4 | 单元测试覆盖率 > 60% | Vitest coverage |
| 7.5 | E2E 测试全部通过 | Playwright CI |
| 7.6 | Chrome/Firefox/Safari 三浏览器一致 | 手动或 BrowserStack |
| 7.7 | README.md 包含完整的本地开发、构建、部署指南 | Code review |
| 7.8 | CSS bundle < 50KB gzipped | Bundle analyzer |

---

## 附录 A: 后端 API 开发清单

以下汇总了前端各阶段需要的新增后端 API。建议在后端项目中按优先级逐步实现。

### A.1 新增 Controller 总览

| Controller | 路由前缀 | 所在 Phase | 优先级 |
|:---|:---|:---|:---|
| `ResearchHistoryController` | `/api/history` | Phase 4+5 | P0 |
| `UserProfileController` | `/api/user` | Phase 5 | P1 |
| `PromptAdminController` | `/api/admin/prompts` | Phase 6 | P0 |
| `AdminAuthFilter` (或方法级别 `@PreAuthorize`) | — | Phase 6 | P0 |

### A.2 详细 API 规范

#### `ResearchHistoryController`

```java
@RestController
@RequestMapping("/api/history")
public class ResearchHistoryController {

    // GET /api/history?userId=&tenantId=&page=0&size=20&status=&keyword=&sortBy=createdAt&sortDir=desc
    @GetMapping
    public Page<ResearchHistoryProjection> listHistory(
        @RequestParam String userId,
        @RequestParam String tenantId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String sortDir
    ) { ... }

    // GET /api/history/{sessionId}
    @GetMapping("/{sessionId}")
    public ResearchHistoryProjection getDetail(@PathVariable String sessionId) { ... }

    // DELETE /api/history/{sessionId}
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String sessionId) { ... }

    // POST /api/history/{sessionId}/re-run
    @PostMapping("/{sessionId}/re-run")
    public ResearchResponse reRun(@PathVariable String sessionId) { ... }
}
```

> **Projection** 说明: 列表接口返回的 `ResearchHistoryProjection` 不应包含 `report` 字段（报告全文可能 > 3000 字），以减少响应体大小。详情接口返回完整实体。

```java
// Spring Data JPA Projection
public interface ResearchHistoryProjection {
    Long getId();
    String getSessionId();
    String getUserId();
    String getTenantId();
    String getQuery();
    int getWordCount();
    int getCitationCount();
    int getIterationCount();
    String getStatus();
    String getEvalScores();
    LocalDateTime getCreatedAt();
}
```

#### `UserProfileController`

```java
@RestController
@RequestMapping("/api/user")
public class UserProfileController {

    // GET /api/user/profile?userId=&tenantId=
    @GetMapping("/profile")
    public UserProfile getProfile(
        @RequestParam String userId,
        @RequestParam String tenantId
    ) { ... }

    // PUT /api/user/preferences
    @PutMapping("/preferences")
    public UserProfile updatePreferences(
        @RequestParam String userId,
        @RequestParam String tenantId,
        @RequestBody Map<String, Object> preferences
    ) { ... }
}
```

#### `PromptAdminController`

```java
@RestController
@RequestMapping("/api/admin/prompts")
@PreAuthorize("hasRole('ADMIN')")
public class PromptAdminController {

    private final PromptTemplateRepository repository;
    private final DynamicPromptService promptService;

    @GetMapping
    public List<PromptTemplateProjection> listAll() { ... }

    @GetMapping("/{id}")
    public PromptTemplateEntity getById(@PathVariable String id) { ... }

    @PutMapping("/{id}")
    public PromptTemplateEntity update(
        @PathVariable String id,
        @RequestBody UpdatePromptRequest request
    ) {
        // 更新 content/status/abGroup
        // @Version 乐观锁自动递增 version
        // promptService.invalidateCache(id)
    }

    @PostMapping("/{id}/reset")
    public PromptTemplateEntity reset(@PathVariable String id) {
        // 从 classpath:prompts/{id}.st 加载默认内容
        // 覆盖 DB 中的 content
        // promptService.invalidateCache(id)
    }

    @PostMapping("/{id}/cache/invalidate")
    public void invalidateCache(@PathVariable String id) {
        promptService.invalidateCache(id);
    }
}

// DTO
record UpdatePromptRequest(
    @Size(min = 10, max = 20000) String content,
    @Pattern(regexp = "active|inactive|deprecated") String status,
    @Pattern(regexp = "A|B") @JsonProperty("abGroup") String abGroup
) {}
```

### A.3 实施顺序建议

```
Phase 0-3 期间 (无需新 API):
  └── 使用已有 POST /api/research + SSE 流

Phase 4 开始前:
  └── [B4.1] GET /api/history/{sessionId} — 获取报告完整内容

Phase 5 开始前:
  └── [B5.1] GET /api/history — 分页历史查询
  └── [B5.3] GET /api/user/profile — 用户画像（可选）

Phase 6 开始前:
  └── [B6.1] GET /api/admin/prompts
  └── [B6.3] PUT /api/admin/prompts/{id}
  └── [B6.4] POST /api/admin/prompts/{id}/reset
  └── [B6.2] GET /api/admin/prompts/{id}
```

---

## 附录 B: 测试用例清单

### B.1 单元测试用例（合计 ~45 个）

| 文件 | 用例数 | 关键场景 |
|:---|:---|:---|
| `lib/__tests__/jwt.test.ts` | 8 | 解析有效/无效 Token、过期判断、存储/清除、过期边界 |
| `lib/__tests__/api.test.ts` | 6 | 成功响应、400/401/500 错误、Header 注入、204 处理 |
| `lib/__tests__/constants.test.ts` | 3 | 枚举完整性、标签映射、颜色映射 |
| `lib/__tests__/markdown.test.ts` | 4 | 组件映射、引用链接识别、标题 ID 生成 |
| `stores/__tests__/auth-store.test.ts` | 5 | login→状态更新、logout→清除、initFromStorage→恢复、过期 Token |
| `stores/__tests__/sse-store.test.ts` | 4 | 事件追加、连接状态、clearSession、getEvents |
| `hooks/__tests__/useResearchSse.test.ts` | 5 | 自动连接、事件累积、COMPLETED 停止、错误重连、unmount 清理 |
| `hooks/__tests__/useResearchQuery.test.ts` | 4 | mutation success→跳转、400→toast、500→toast、429→toast |
| `hooks/__tests__/useHistoryList.test.ts` | 4 | 首页加载、翻页、搜索参数、空结果 |
| `components/research/__tests__/ResearchInput.test.tsx` | 5 | 字符计数、空值校验→disabled、模式切换、提交调用、API 错误 toast |
| `components/research/__tests__/WorkflowTimeline.test.tsx` | 5 | 初始全 pending、逐个 done、缓存命中跳过、dual_search 双列、错误节点 |
| `components/research/__tests__/SseStatusBadge.test.tsx` | 4 | connected/connecting/disconnected/error 四种渲染 |
| `components/research/__tests__/EvalRadarChart.test.tsx` | 3 | 正常数据、零分、空数据 |
| `components/research/__tests__/ReportOutline.test.tsx` | 4 | 标题提取、嵌套树、点击跳转、IntersectionObserver |
| `components/history/__tests__/HistoryFilters.test.tsx` | 3 | 状态筛选、日期范围、评分筛选 |
| `components/admin/__tests__/PromptTable.test.tsx` | 3 | 列表渲染、状态徽章、AB 分组 |
| `components/admin/__tests__/PromptEditor.test.tsx` | 4 | 字段绑定、保存成功、保存失败、表单校验 |

### B.2 E2E 测试用例（合计 ~12 个）

| # | 用例名 | 步骤摘要 |
|:---|:---|:---|
| E2E-01 | 完整研究流程 | 首页输入→提交→SSE 进度→Timeline 7 节点变绿→报告渲染→评估雷达图 |
| E2E-02 | 缓存命中流程 | 提交相同查询→CacheHitBanner 显示→SSE 立即完成 |
| E2E-03 | 查询为空 | 不输入→提交按钮 disabled |
| E2E-04 | 注入检测 | 提交包含敏感词→400 错误→toast "请求被拒绝" |
| E2E-05 | SSE 断连重连 | 断网→状态灰→联网→自动重连→Timeline 继续 |
| E2E-06 | 历史列表分页 | 访问 /history→验证 20 条→点击"下一页"→验证新数据→滚动到顶部 |
| E2E-07 | 历史搜索筛选 | 输入关键词→列表过滤→清除→恢复→状态筛选→组合筛选 |
| E2E-08 | 查看历史详情 | 点击历史记录→跳转→完整报告渲染→大纲导航→引用 Popover |
| E2E-09 | 删除历史 | 点击删除→确认→列表不再显示→toast 确认 |
| E2E-10 | Prompt 编辑保存 | 打开 admin→点击编辑→修改 content→保存→version+1→toast 显示 |
| E2E-11 | Prompt 重置 | 点击重置→二次确认→content 还原→version+1 |
| E2E-12 | 管理员权限守卫 | 非 admin 用户→访问 /admin/prompts→重定向到首页 |

### B.3 运行测试命令

```bash
# 单元测试 + 集成测试
cd ui/
pnpm test                    # 运行一次
pnpm test -- --watch         # watch 模式
pnpm test -- --coverage      # 生成覆盖率报告

# E2E 测试
pnpm exec playwright install --with-deps chromium
pnpm build
pnpm start &                 # 启动生产构建
pnpm exec playwright test    # 运行 E2E

# 单个测试文件
pnpm test -- src/lib/__tests__/jwt.test.ts
pnpm exec playwright test --grep "完整研究流程"
```

---

## 附录 C: 快速参考 — 各阶段关键命令

```bash
# Phase 0
pnpm create next-app@latest . --typescript --tailwind --eslint --app --src-dir --import-alias "@/*" --no-turbopack
pnpm add @microsoft/fetch-event-source zustand @tanstack/react-query react-markdown remark-gfm rehype-raw rehype-highlight recharts framer-motion sonner dayjs react-hook-form @hookform/resolvers zod next-themes lucide-react clsx tailwind-merge
pnpm dlx shadcn@latest init
pnpm dlx shadcn@latest add button card input textarea badge skeleton toast tooltip dropdown-menu sheet tabs separator progress hover-card

# Phase 1-3 (开发)
pnpm dev                      # 启动开发服务器
pnpm lint                     # ESLint 检查
pnpm typecheck                # TypeScript 类型检查

# Phase 5-6（额外 shadcn 组件）
pnpm dlx shadcn@latest add table select dialog command popover

# Phase 7
pnpm build                    # 生产构建
pnpm start                    # 生产运行
pnpm test -- --coverage       # 测试 + 覆盖率
pnpm exec playwright test     # E2E
npx next-bundle-analyzer      # Bundle 分析（需配置）
```

---

> 📌 **总结**: 本实施计划将前端工程拆解为 7 个可验证的阶段，从脚手架搭建到最终打磨，预计总工时 17 天。后端需要新增 4 个 Controller（共享约 10 个 API 端点）以支撑研究历史、评估展示和管理后台功能。每个阶段都有明确的验收标准和测试策略，可独立验证交付质量。
