/**
 * JWT Token 管理
 *
 * 开发模式 (DEV_MODE=true): 使用 mock token 绕过认证。
 * 生产模式: localStorage 存储 JWT，支持自动刷新。
 */

const TOKEN_KEY = 'deepresearch_token';
const REFRESH_TOKEN_KEY = 'deepresearch_refresh_token';

// =========================== Token 读写 ===========================

export function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  if (process.env.NEXT_PUBLIC_DEV_MODE === 'true') return getDevToken();
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
  sub: string;
  tenant_id?: string;
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
  if (process.env.NEXT_PUBLIC_DEV_MODE === 'true') return getDevToken();

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

// =========================== 开发模式 ===========================

let cachedDevToken: string | null = null;

function getDevToken(): string {
  if (cachedDevToken) return cachedDevToken;
  const header = btoa(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const devPayload = {
    sub: process.env.NEXT_PUBLIC_DEV_USER_ID || 'dev-user',
    tenant_id: process.env.NEXT_PUBLIC_DEV_TENANT_ID || 'default',
    authorities: ['ROLE_USER', 'ROLE_ADMIN'],
    exp: 9999999999,
    iat: 1,
  };
  const payload = btoa(JSON.stringify(devPayload));
  cachedDevToken = `${header}.${payload}.dev-signature`;
  return cachedDevToken;
}

/** 从 JWT 中提取 userId */
export function getUserId(): string {
  const token = getToken();
  if (!token) return 'anonymous';
  if (process.env.NEXT_PUBLIC_DEV_MODE === 'true') {
    return process.env.NEXT_PUBLIC_DEV_USER_ID || 'dev-user';
  }
  const decoded = decodeToken(token);
  return decoded?.sub || 'anonymous';
}

/** 从 JWT 中提取 tenantId */
export function getTenantId(): string {
  const token = getToken();
  if (!token) return 'default';
  if (process.env.NEXT_PUBLIC_DEV_MODE === 'true') {
    return process.env.NEXT_PUBLIC_DEV_TENANT_ID || 'default';
  }
  const decoded = decodeToken(token);
  return decoded?.tenant_id || 'default';
}

/** 检查是否为 admin */
export function isAdmin(): boolean {
  const token = getToken();
  if (!token) return false;
  if (process.env.NEXT_PUBLIC_DEV_MODE === 'true') return true;
  const decoded = decodeToken(token);
  return decoded?.authorities?.includes('ROLE_ADMIN') ?? false;
}
