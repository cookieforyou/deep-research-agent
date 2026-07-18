/**
 * JWT Token 管理
 *
 * 集成 Casdoor OAuth2 Password Grant 认证。
 * localStorage 存储 JWT，支持自动刷新。
 *
 * JWT Payload 关键字段（Casdoor）:
 *   - owner: 组织/租户标识（如 "tenant_002"）
 *   - name:  用户名（如 "user_10000"）
 *   - sub:   UUID（无意义，不可用作 userId）
 *   - isAdmin: 是否管理员
 *
 * userId 解析: `${owner}/${name}`（对齐后端 TenantJwtAuthenticationConverter.resolveUserId()）
 * tenantId 解析: `owner`（Casdoor JWT 无 tenant_id claim，回退 owner）
 */

const TOKEN_KEY = 'deepresearch_token';
const REFRESH_TOKEN_KEY = 'deepresearch_refresh_token';

// =========================== Token 读写 ===========================

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

// =========================== JWT 解析 ===========================

interface JwtPayload {
  /** Casdoor UUID — 无意义，不可用作 userId */
  sub: string;
  /** 组织/租户（如 "tenant_002"） */
  owner?: string;
  /** 用户名（如 "user_10000"） */
  name?: string;
  /** 是否管理员 */
  isAdmin?: boolean;
  /** 过期时间 (Unix timestamp) */
  exp: number;
  /** 签发时间 */
  iat: number;
  /** Spring Security authorities（如果有） */
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
  return Date.now() / 1000 >= decoded.exp - 60;
}

// =========================== Token 刷新 ===========================

let refreshPromise: Promise<string | null> | null = null;

/**
 * 尝试刷新 access token。
 * 使用单例 Promise 避免并发请求时重复刷新。
 *
 * @returns 新的 access token，失败时返回 null
 */
export async function refreshAccessToken(): Promise<string | null> {
  if (typeof window === 'undefined') return null;

  // 复用进行中的刷新请求
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    try {
      const refreshToken = getRefreshToken();
      if (!refreshToken) return null;

      const res = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });

      if (!res.ok) {
        removeToken();
        return null;
      }

      const data = await res.json();
      setToken(data.accessToken);
      if (data.refreshToken) setRefreshToken(data.refreshToken);
      return data.accessToken;
    } catch {
      return null;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

/**
 * 获取有效的 access token（必要时自动刷新）。
 * 刷新失败时返回 null，调用方应重定向到登录页。
 */
export async function getValidToken(): Promise<string | null> {
  const token = getToken();
  if (!token) return null;
  if (!isTokenExpired(token)) return token;
  return refreshAccessToken();
}

// =========================== 用户信息提取 ===========================

/**
 * 从 JWT 中提取 userId。
 * 对齐后端 TenantJwtAuthenticationConverter.resolveUserId():
 *   userId = owner/name（如 "tenant_002/user_10000"）
 * 回退到 sub（非 Casdoor JWT 时）。
 */
export function getUserId(): string {
  const token = getToken();
  if (!token) return 'anonymous';
  const decoded = decodeToken(token);
  if (!decoded) return 'anonymous';
  if (decoded.owner && decoded.name) {
    return `${decoded.owner}/${decoded.name}`;
  }
  return decoded.sub || 'anonymous';
}

/**
 * 从 JWT 中提取 tenantId。
 * 对齐后端 TenantJwtAuthenticationConverter.resolveTenantId():
 *   tenant_id claim 优先 → 回退 owner
 * Casdoor JWT 无 tenant_id claim，使用 owner。
 */
export function getTenantId(): string {
  const token = getToken();
  if (!token) return 'default';
  const decoded = decodeToken(token);
  if (!decoded) return 'default';
  return decoded.owner || 'default';
}

/**
 * 检查是否为 admin。
 * Casdoor JWT 有 isAdmin 字段；非 Casdoor JWT 回退检查 authorities。
 */
export function isAdmin(): boolean {
  const token = getToken();
  if (!token) return false;
  const decoded = decodeToken(token);
  if (!decoded) return false;
  if (typeof decoded.isAdmin === 'boolean') return decoded.isAdmin;
  return decoded.authorities?.includes('ROLE_ADMIN') ?? false;
}
