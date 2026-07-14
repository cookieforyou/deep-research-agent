/**
 * JWT Token 管理
 *
 * 当前模式：开发阶段使用 DEV_MODE 绕过认证。
 * DEV_MODE=true 时，使用 mock token 直接与后端通信
 * （需后端 SecurityConfig dev profile 同时放通 /api/**）。
 *
 * 生产环境：通过 localStorage 存储 JWT，自动注入 Authorization header。
 */

const TOKEN_KEY = 'deepresearch_token';
const REFRESH_TOKEN_KEY = 'deepresearch_refresh_token';

// =========================== Token 读写 ===========================

export function getToken(): string | null {
  if (typeof window === 'undefined') return null;

  // 开发模式：返回 mock token
  if (process.env.NEXT_PUBLIC_DEV_MODE === 'true') {
    return getDevToken();
  }

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
  // 提前 60 秒视为过期，防止网络延迟导致 401
  return Date.now() / 1000 >= decoded.exp - 60;
}

// =========================== 开发模式 ===========================

let cachedDevToken: string | null = null;

function getDevToken(): string {
  if (cachedDevToken) return cachedDevToken;

  // 构造一个简单的 JWT（仅用于开发，后端需放通校验）
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

/** 从 JWT（或 dev mock）中提取 userId */
export function getUserId(): string {
  const token = getToken();
  if (!token) return 'anonymous';

  if (process.env.NEXT_PUBLIC_DEV_MODE === 'true') {
    return process.env.NEXT_PUBLIC_DEV_USER_ID || 'dev-user';
  }

  const decoded = decodeToken(token);
  return decoded?.sub || 'anonymous';
}

/** 从 JWT（或 dev mock）中提取 tenantId */
export function getTenantId(): string {
  const token = getToken();
  if (!token) return 'default';

  if (process.env.NEXT_PUBLIC_DEV_MODE === 'true') {
    return process.env.NEXT_PUBLIC_DEV_TENANT_ID || 'default';
  }

  const decoded = decodeToken(token);
  return decoded?.tenant_id || 'default';
}

/** 检查当前用户是否具有 admin 角色 */
export function isAdmin(): boolean {
  const token = getToken();
  if (!token) return false;

  if (process.env.NEXT_PUBLIC_DEV_MODE === 'true') {
    return true; // dev mode 默认 admin
  }

  const decoded = decodeToken(token);
  return decoded?.authorities?.includes('ROLE_ADMIN') ?? false;
}
