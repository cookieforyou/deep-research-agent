'use client';

import { useState } from 'react';
import { useUserList } from '@/hooks/useUserManagement';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Badge } from '@/components/ui/badge';
import { AlertCircle, RefreshCw, Search, Users } from 'lucide-react';
import dayjs from 'dayjs';
import type { UserSummary } from '@/lib/types';

/**
 * 用户管理表格（只读仪表盘）。
 *
 * 展示全部用户的画像数据：研究次数、兴趣标签、偏好、最近活跃时间。
 */
export function UserTable() {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [expandedUsers, setExpandedUsers] = useState<Set<string>>(new Set());
  const pageSize = 20;

  const toggleExpand = (userId: string) => {
    setExpandedUsers((prev) => {
      const next = new Set(prev);
      if (next.has(userId)) {
        next.delete(userId);
      } else {
        next.add(userId);
      }
      return next;
    });
  };

  const { data, isLoading, isError, refetch } = useUserList({ page, size: pageSize, search: search || undefined });

  const handleSearch = () => {
    setPage(0);
    setSearch(searchInput);
  };

  // 加载中
  if (isLoading) {
    return (
      <div className="space-y-2">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="flex items-center gap-4 p-4 border rounded-lg">
            <Skeleton className="h-5 w-40" />
            <Skeleton className="h-5 w-24" />
            <Skeleton className="h-5 w-16" />
            <Skeleton className="h-5 w-20" />
            <Skeleton className="h-5 w-32" />
          </div>
        ))}
      </div>
    );
  }

  // 错误
  if (isError) {
    return (
      <div className="flex flex-col items-center gap-3 py-12 text-center">
        <AlertCircle className="h-8 w-8 text-destructive" />
        <p className="text-sm text-muted-foreground">加载用户列表失败</p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          <RefreshCw className="h-4 w-4 mr-2" />
          重试
        </Button>
      </div>
    );
  }

  const pageData = data;
  const users: UserSummary[] = pageData?.content || [];
  const totalPages = pageData?.totalPages || 0;
  const totalElements = pageData?.totalElements || 0;

  return (
    <div className="space-y-4">
      {/* 搜索栏 */}
      <div className="flex items-center gap-2">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="搜索 userId / tenantId..."
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            className="pl-8 h-8 text-xs"
          />
        </div>
        <Button variant="outline" size="sm" className="text-xs" onClick={handleSearch}>
          搜索
        </Button>
        <div className="flex-1" />
        <p className="text-xs text-muted-foreground">
          共 {totalElements} 个用户
        </p>
      </div>

      {/* 表格 */}
      <div className="rounded-lg border overflow-hidden">
        <table className="w-full">
          <thead className="bg-muted/50">
            <tr>
              <th className="text-left text-xs font-medium text-muted-foreground py-3 px-4 w-[200px]">用户</th>
              <th className="text-left text-xs font-medium text-muted-foreground py-3 px-2 w-[120px]">租户</th>
              <th className="text-center text-xs font-medium text-muted-foreground py-3 px-2 w-[80px]">研究次数</th>
              <th className="text-left text-xs font-medium text-muted-foreground py-3 px-2">兴趣标签</th>
              <th className="text-left text-xs font-medium text-muted-foreground py-3 px-2">偏好</th>
              <th className="text-right text-xs font-medium text-muted-foreground py-3 px-4 w-[140px]">最近活跃</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.userId} className="border-b hover:bg-muted/50 transition-colors">
                <td className="py-3 px-4">
                  <span className="text-sm font-medium">{user.userId}</span>
                </td>
                <td className="py-3 px-2">
                  <code className="text-xs text-muted-foreground">{user.tenantId}</code>
                </td>
                <td className="py-3 px-2 text-center">
                  <Badge variant="secondary" className="text-xs">{user.researchCount}</Badge>
                </td>
                <td className="py-3 px-2">
                  <div className="flex flex-wrap gap-1">
                    {(expandedUsers.has(user.userId) ? user.interests : user.interests.slice(0, 3)).map((tag) => (
                      <Badge key={tag} variant="outline" className="text-[10px]" title={tag}>
                        {tag.length > 16 ? tag.slice(0, 15) + '…' : tag}
                      </Badge>
                    ))}
                    {!expandedUsers.has(user.userId) && user.interests.length > 3 && (
                      <button
                        onClick={() => toggleExpand(user.userId)}
                        className="text-[10px] text-muted-foreground hover:text-foreground hover:underline cursor-pointer"
                        title="展开全部标签"
                      >
                        +{user.interests.length - 3}
                      </button>
                    )}
                    {expandedUsers.has(user.userId) && user.interests.length > 3 && (
                      <button
                        onClick={() => toggleExpand(user.userId)}
                        className="text-[10px] text-muted-foreground hover:text-foreground hover:underline cursor-pointer"
                        title="收起"
                      >
                        收起
                      </button>
                    )}
                  </div>
                </td>
                <td className="py-3 px-2">
                  <span className="text-xs text-muted-foreground">
                    {Object.entries(user.preferences || {}).length > 0
                      ? Object.entries(user.preferences)
                          .slice(0, 2)
                          .map(([k, v]) => `${k}: ${v}`)
                          .join(' · ')
                      : '—'}
                  </span>
                </td>
                <td className="py-3 px-4 text-right text-xs text-muted-foreground whitespace-nowrap">
                  {dayjs(user.updatedAt).format('MM-DD HH:mm')}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* 空状态 */}
      {users.length === 0 && (
        <div className="text-center py-12 text-muted-foreground">
          <Users className="h-8 w-8 mx-auto mb-2 opacity-50" />
          <p className="text-sm">暂无用户数据</p>
          <p className="text-xs mt-1">研究过程中会自动创建用户画像</p>
        </div>
      )}

      {/* 分页 */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between pt-2">
          <p className="text-xs text-muted-foreground">
            第 {page + 1} / {totalPages} 页
          </p>
          <div className="flex gap-1">
            <Button
              variant="outline"
              size="sm"
              className="text-xs"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              上一页
            </Button>
            <Button
              variant="outline"
              size="sm"
              className="text-xs"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              下一页
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
