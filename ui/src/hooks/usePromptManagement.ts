'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/api';
import { toast } from 'sonner';

/**
 * Prompt 模板管理 Hook
 *
 * 提供 Prompt 模板的 CRUD 操作和状态管理。
 */
export function usePromptList() {
  return useQuery({
    queryKey: ['admin', 'prompts'],
    queryFn: () => adminApi.listPrompts(),
    staleTime: 30_000,
    retry: 1,
  });
}

export function useUpdatePrompt() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      data,
    }: {
      id: string;
      data: { content?: string; status?: string; abGroup?: string | null };
    }) => adminApi.updatePrompt(id, data),
    onSuccess: (_, variables) => {
      toast.success(`模板 "${variables.id}" 已更新，将在 1 分钟内自动生效`);
      queryClient.invalidateQueries({ queryKey: ['admin', 'prompts'] });
    },
    onError: (error: Error) => {
      toast.error(`更新失败: ${error.message}`);
    },
  });
}

export function useResetPrompt() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => adminApi.resetPrompt(id),
    onSuccess: (_, id) => {
      toast.success(`模板 "${id}" 已重置为默认值`);
      queryClient.invalidateQueries({ queryKey: ['admin', 'prompts'] });
    },
    onError: (error: Error) => {
      toast.error(`重置失败: ${error.message}`);
    },
  });
}

export function useBatchUpdateAbGroup() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (items: { id: string; abGroup: string | null }[]) =>
      adminApi.batchUpdateAbGroup(items),
    onSuccess: (data) => {
      toast.success(`已更新 ${data.length} 个模板的 A/B 分组`);
      queryClient.invalidateQueries({ queryKey: ['admin', 'prompts'] });
    },
    onError: (error: Error) => {
      toast.error(`批量更新失败: ${error.message}`);
    },
  });
}
