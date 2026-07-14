import { getToken } from './jwt';
import type {
  ResearchRequest,
  ResearchResponse,
  ResearchHistoryItem,
  PaginatedResponse,
  PromptTemplate,
  UserProfile,
  ProblemDetail,
} from './types';
import { ApiError } from './types';

const API_BASE = '/api';

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
// Phase 5 需要后端新增 ResearchHistoryController

export const historyApi = {
  /** GET /api/history — 分页查询研究历史 */
  list(params: {
    userId: string;
    tenantId: string;
    page?: number;
    size?: number;
    status?: string;
    keyword?: string;
    sortBy?: string;
    sortDir?: string;
  }) {
    const searchParams = new URLSearchParams();
    searchParams.set('userId', params.userId);
    searchParams.set('tenantId', params.tenantId);
    if (params.page !== undefined) searchParams.set('page', String(params.page));
    if (params.size !== undefined) searchParams.set('size', String(params.size));
    if (params.status) searchParams.set('status', params.status);
    if (params.keyword) searchParams.set('keyword', params.keyword);
    if (params.sortBy) searchParams.set('sortBy', params.sortBy);
    if (params.sortDir) searchParams.set('sortDir', params.sortDir);
    return request<PaginatedResponse<ResearchHistoryItem>>(`/history?${searchParams}`);
  },

  /** GET /api/history/{sessionId} — 获取历史详情（含完整报告，验证所有权） */
  getDetail(sessionId: string, userId?: string, tenantId?: string) {
    const params = new URLSearchParams();
    if (userId) params.set('userId', userId);
    if (tenantId) params.set('tenantId', tenantId);
    const qs = params.toString();
    return request<ResearchHistoryItem>(`/history/${sessionId}${qs ? '?' + qs : ''}`);
  },

  /** DELETE /api/history/{sessionId} — 删除研究记录（需所有权验证） */
  delete(sessionId: string, userId: string, tenantId: string) {
    const params = new URLSearchParams();
    params.set('userId', userId);
    params.set('tenantId', tenantId);
    return request<void>(`/history/${sessionId}?${params}`, { method: 'DELETE' });
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
};

// =========================== 用户 API ===========================
// Phase 5 需要后端新增 UserProfileController

export const userApi = {
  /** GET /api/user/profile — 获取用户画像 */
  getProfile(userId: string, tenantId: string) {
    return request<UserProfile>(`/user/profile?userId=${userId}&tenantId=${tenantId}`);
  },
};
