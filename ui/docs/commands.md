## 前端（ui）本地启动指南

### 项目概览

- 框架: Next.js 15 (App Router) + React 19
- 样式: Tailwind CSS v4 + shadcn/ui
- 包管理: pnpm
- 状态管理: TanStack Query v5 + Zustand v5

### 前置条件

1. Node.js >= 18（推荐 20+）
2. pnpm 已全局安装（如没有：npm install -g pnpm）
3. 后端服务已在 http://localhost:8080 运行（可选 — 前端可以独立启动，但 API 调用会失败）

### 启动步骤

1. 进入 ui 目录：`cd ui`
2. 安装依赖：`pnpm install`
3. 启动开发服务器：`pnpm dev`

启动后访问 http://localhost:3000

### 环境变量

`.env.local` 已配置好开发模式：

| 变量 | 值                    | 说明                    |
|:---|:----------------------|:------------------------|
| BACKEND_URL | http://localhost:8080 | 后端 API 地址           |
| NEXT_PUBLIC_DEV_MODE | true                  | 跳过 JWT 认证（开发用） |
| NEXT_PUBLIC_DEV_USER_ID | dev-user              | 开发模式用户 ID         |
| NEXT_PUBLIC_DEV_TENANT_ID | default               | 开发模式租户 ID         |

**重要**：DEV_MODE=true 会直接绕过登录页，使用 dev-user 身份访问，适合本地开发。

### 其他脚本

```
pnpm build              # 构建生产版本
pnpm start              # 启动生产构建
pnpm lint               # ESLint 检查
pnpm typecheck          # TypeScript 类型检查
pnpm test               # 运行单元测试（Vitest）
pnpm test:coverage      # 带覆盖率测试
```

### 与后端联调

前端通过 Next.js 的 rewrites 代理 API 请求：
- `/api/*` → `BACKEND_URL/api/*`
- `/actuator/*` → `BACKEND_URL/actuator/*`

所以后端启动后，前端会自动转发请求，无需额外配置 CORS。

### 验证启动成功

启动后浏览器访问 http://localhost:3000，应看到：
1. DeepResearch 首页（研究输入框 + 示例问题）
2. 无需登录（DEV_MODE=true）
3. 如果后端未启动，研究功能会报错，但页面本身正常渲染

