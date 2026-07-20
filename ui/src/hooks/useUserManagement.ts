'use client';

import { useQuery } from '@tanstack/react-query';
import { adminApi } from '@/lib/api';

/**
 * 用户管理 Hook
 *
 * 管理员用户列表（只读仪表盘）。
 */
export function useUserList(params?: { page?: number; size?: number; search?: string }) {
  return useQuery({
    queryKey: ['admin', 'users', params],
    queryFn: () => adminApi.listUsers(params),
    staleTime: 30_000,
    retry: 1,
  });
}
