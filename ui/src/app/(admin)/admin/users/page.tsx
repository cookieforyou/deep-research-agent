import { UserTable } from '@/components/admin/UserTable';
import { Users } from 'lucide-react';

/**
 * 用户管理页 — 管理员只读仪表盘。
 *
 * 展示 user_profile 表中所有用户的研究统计信息：
 * userId、tenantId、研究次数、兴趣标签、偏好、最近活跃时间。
 */
export default function UsersPage() {
  return (
    <div className="max-w-5xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold flex items-center gap-2">
          <Users className="h-5 w-5" />
          用户管理
        </h1>
        <p className="text-sm text-muted-foreground mt-1">
          查看所有用户的画像数据和研究统计。数据在研究过程中自动收集。
        </p>
      </div>

      <UserTable />
    </div>
  );
}
