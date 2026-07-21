import { getToken } from './jwt';
import type {
  ResearchRequest,
  ResearchResponse,
  ResearchHistoryItem,
  ResearchHistoryDetail,
  PaginatedResponse,
  PromptTemplate,
  UserProfile,
  UserSummary,
  ProblemDetail,
} from './types';
import { ApiError } from './types';

const API_BASE = '/api';

/** Casdoor OAuth2 Token 端点 */
const AUTH_BASE = process.env.NEXT_PUBLIC_AUTH_URL || 'https://auth.hyperinfer.top';
/** Casdoor Client ID */
const AUTH_CLIENT_ID = process.env.NEXT_PUBLIC_AUTH_CLIENT_ID || '5077a6f18ed9b31be437';

// =========================== 基础请求 ===========================

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
    const error: ProblemDetail = await res.json().catch(() => ({
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
  /** POST /api/research — 发起深度研究 */
  startResearch(input: ResearchRequest) {
    return request<ResearchResponse>('/research', {
      method: 'POST',
      body: JSON.stringify(input),
    });
  },

  /** GET /api/research/{sessionId} — 轮询研究状态 */
  getStatus(sessionId: string) {
    return request<ResearchResponse>(`/research/${sessionId}`);
  },
};

// =========================== 历史 API ===========================
// 身份（userId/tenantId）由后端从 JWT 提取，前端无需传参。

export const historyApi = {
  /** GET /api/history — 分页查询研究历史 */
  list(params: {
    page?: number;
    size?: number;
    sortBy?: string;
    sortDir?: string;
  }) {
    const searchParams = new URLSearchParams();
    if (params.page !== undefined) searchParams.set('page', String(params.page));
    if (params.size !== undefined) searchParams.set('size', String(params.size));
    if (params.sortBy) searchParams.set('sortBy', params.sortBy);
    if (params.sortDir) searchParams.set('sortDir', params.sortDir);
    return request<PaginatedResponse<ResearchHistoryItem>>(`/history?${searchParams}`);
  },

  /** GET /api/history/{sessionId} — 获取历史详情（自动所有权验证，含完整报告） */
  getDetail(sessionId: string) {
    return request<ResearchHistoryDetail>(`/history/${sessionId}`);
  },

  /** DELETE /api/history/{sessionId} — 删除研究记录（自动所有权验证） */
  delete(sessionId: string) {
    return request<void>(`/history/${sessionId}`, { method: 'DELETE' });
  },

  /** POST /api/history/{sessionId}/re-run — 重新执行相同查询（预留） */
  reRun(sessionId: string) {
    return request<ResearchResponse>(`/history/${sessionId}/re-run`, {
      method: 'POST',
    });
  },
};

// =========================== 管理 API ===========================
// Phase 6 需要后端新增 PromptAdminController

export const adminApi = {
  /** GET /api/admin/prompts — 获取全部 Prompt 模板 */
  listPrompts() {
    return request<PromptTemplate[]>('/admin/prompts');
  },

  /** GET /api/admin/prompts/{id} — 获取单个模板详情 */
  getPrompt(id: string) {
    return request<PromptTemplate>(`/admin/prompts/${id}`);
  },

  /** PUT /api/admin/prompts/{id} — 更新模板内容/状态/AB分组 */
  updatePrompt(
    id: string,
    data: { content?: string; status?: string; abGroup?: string | null },
  ) {
    return request<PromptTemplate>(`/admin/prompts/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  /** POST /api/admin/prompts/{id}/reset — 重置为 classpath 默认值 */
  resetPrompt(id: string) {
    return request<PromptTemplate>(`/admin/prompts/${id}/reset`, {
      method: 'POST',
    });
  },

  /** POST /api/admin/prompts/batch-ab-group — 批量更新 A/B 分组 */
  batchUpdateAbGroup(items: { id: string; abGroup: string | null }[]) {
    return request<PromptTemplate[]>('/admin/prompts/batch-ab-group', {
      method: 'POST',
      body: JSON.stringify({ items }),
    });
  },

  /** GET /api/admin/users — 分页查询用户列表（管理员） */
  listUsers(params?: { page?: number; size?: number; search?: string }) {
    const sp = new URLSearchParams();
    if (params?.page !== undefined) sp.set('page', String(params.page));
    if (params?.size !== undefined) sp.set('size', String(params.size));
    if (params?.search) sp.set('search', params.search);
    const qs = sp.toString();
    return request<PaginatedResponse<UserSummary>>(`/admin/users${qs ? `?${qs}` : ''}`);
  },
};

// =========================== 用户 API ===========================
// 身份（userId/tenantId）由后端从 JWT 提取。

export const userApi = {
  /** GET /api/user/profile — 获取当前用户画像 */
  getProfile() {
    return request<UserProfile>('/user/profile');
  },
};

// =========================== 认证 API ===========================

export interface AuthTokenResponse {
  access_token: string;
  id_token: string;
  refresh_token: string;
  token_type: string;
  expires_in: number;
  scope: string;
}

export interface AuthErrorResponse {
  error: string;
  error_description?: string;
}

/**
 * Casdoor OAuth2 Password Grant 登录。
 *
 * POST https://auth.hyperinfer.top/api/login/oauth/access_token
 * Content-Type: application/x-www-form-urlencoded
 */
export async function loginWithPassword(
  username: string,
  password: string,
): Promise<AuthTokenResponse> {
  const params = new URLSearchParams();
  params.set('grant_type', 'password');
  params.set('scope', 'read');
  params.set('client_id', AUTH_CLIENT_ID);
  params.set('username', username);
  params.set('password', password);

  const res = await fetch(`${AUTH_BASE}/api/login/oauth/access_token`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: params.toString(),
  });

  if (!res.ok) {
    const err: AuthErrorResponse = await res.json().catch(() => ({
      error: 'network_error',
      error_description: `HTTP ${res.status}: ${res.statusText}`,
    }));
    throw new Error(err.error_description || err.error || '登录失败');
  }

  return res.json() as Promise<AuthTokenResponse>;
}
