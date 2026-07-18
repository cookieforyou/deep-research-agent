'use client';

import { create } from 'zustand';
import {
  getToken,
  setToken,
  removeToken,
  setRefreshToken,
  decodeToken,
  isTokenExpired,
  getUserId,
  getTenantId,
  isAdmin,
} from '@/lib/jwt';

interface AuthState {
  /** JWT Token 字符串 */
  token: string | null;
  /** 从 JWT payload 解析的用户 ID（owner/name） */
  userId: string;
  /** 从 JWT payload 解析的租户 ID（owner） */
  tenantId: string;
  /** 是否已认证 */
  isAuthenticated: boolean;
  /** 是否为管理员 */
  isAdmin: boolean;

  /** 登录 / 设置 token */
  login: (token: string, refreshToken?: string) => void;
  /** 登出 / 清除 token */
  logout: () => void;
  /** 从 localStorage 初始化状态（应用启动时调用） */
  initFromStorage: () => void;
}

/**
 * 认证状态管理
 *
 * 从 localStorage 读取 Casdoor JWT，解析 userId（owner/name）、
 * tenantId（owner）、isAdmin。
 */
export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  userId: '',
  tenantId: '',
  isAuthenticated: false,
  isAdmin: false,

  login: (token, refreshToken) => {
    setToken(token);
    if (refreshToken) {
      setRefreshToken(refreshToken);
    }
    const decoded = decodeToken(token);
    // userId = owner/name（对齐后端 resolveUserId）
    const userId =
      decoded?.owner && decoded?.name
        ? `${decoded.owner}/${decoded.name}`
        : decoded?.sub || 'anonymous';
    // tenantId = owner（Casdoor JWT 无 tenant_id claim）
    const tenantId = decoded?.owner || 'default';
    // isAdmin: Casdoor 有 isAdmin 字段，回退 authorities
    const admin =
      typeof decoded?.isAdmin === 'boolean'
        ? decoded.isAdmin
        : decoded?.authorities?.includes('ROLE_ADMIN') ?? false;

    set({
      token,
      userId,
      tenantId,
      isAdmin: admin,
      isAuthenticated: true,
    });
  },

  logout: () => {
    removeToken();
    set({
      token: null,
      userId: '',
      tenantId: '',
      isAdmin: false,
      isAuthenticated: false,
    });
  },

  initFromStorage: () => {
    const token = getToken();
    if (token && !isTokenExpired(token)) {
      const decoded = decodeToken(token);
      const userId =
        decoded?.owner && decoded?.name
          ? `${decoded.owner}/${decoded.name}`
          : decoded?.sub || 'anonymous';
      const tenantId = decoded?.owner || 'default';
      const admin =
        typeof decoded?.isAdmin === 'boolean'
          ? decoded.isAdmin
          : decoded?.authorities?.includes('ROLE_ADMIN') ?? false;

      set({
        token,
        userId,
        tenantId,
        isAdmin: admin,
        isAuthenticated: true,
      });
    } else if (token) {
      // Token 已过期，清除
      removeToken();
    }
  },
}));
