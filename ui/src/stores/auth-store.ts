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
  /** 从 JWT payload 解析的用户 ID */
  userId: string;
  /** 从 JWT payload 解析的租户 ID */
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
 * 开发模式 (NEXT_PUBLIC_DEV_MODE=true):
 *   使用 mock token，userId="dev-user", tenantId="default", isAdmin=true
 *
 * 生产模式:
 *   从 localStorage 读取 JWT，解析 userId/tenantId/authorities
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
    set({
      token,
      userId: decoded?.sub || 'anonymous',
      tenantId: decoded?.tenant_id || 'default',
      isAdmin: decoded?.authorities?.includes('ROLE_ADMIN') ?? false,
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
    // 开发模式：直接使用 mock 身份
    if (process.env.NEXT_PUBLIC_DEV_MODE === 'true') {
      set({
        token: getToken(),
        userId: getUserId(),
        tenantId: getTenantId(),
        isAdmin: isAdmin(),
        isAuthenticated: true,
      });
      return;
    }

    // 生产模式：从 localStorage 恢复
    const token = getToken();
    if (token && !isTokenExpired(token)) {
      const decoded = decodeToken(token);
      set({
        token,
        userId: decoded?.sub || 'anonymous',
        tenantId: decoded?.tenant_id || 'default',
        isAdmin: decoded?.authorities?.includes('ROLE_ADMIN') ?? false,
        isAuthenticated: true,
      });
    } else if (token) {
      // Token 已过期，清除
      removeToken();
    }
  },
}));
