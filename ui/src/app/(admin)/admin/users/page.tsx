import { Card, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Users, Shield, BarChart3 } from 'lucide-react';

/**
 * 用户管理页（预留）。
 *
 * 后续可扩展功能：
 * - 用户列表（表格）
 * - 角色分配
 * - 使用统计
 * - 配额管理
 */
export default function UsersPage() {
  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">用户管理</h1>
        <p className="text-sm text-muted-foreground mt-1">
          管理系统用户、角色权限和研究配额。
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2 mb-1">
              <Users className="h-4 w-4 text-muted-foreground" />
              <Badge variant="secondary" className="text-xs">预留</Badge>
            </div>
            <CardTitle className="text-base">用户列表</CardTitle>
            <CardDescription className="text-xs">
              查看和管理所有注册用户，支持搜索、筛选和批量操作。
            </CardDescription>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2 mb-1">
              <Shield className="h-4 w-4 text-muted-foreground" />
              <Badge variant="secondary" className="text-xs">预留</Badge>
            </div>
            <CardTitle className="text-base">角色权限</CardTitle>
            <CardDescription className="text-xs">
              分配用户角色（User / Admin），控制 Prompt 管理和系统配置访问权限。
            </CardDescription>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2 mb-1">
              <BarChart3 className="h-4 w-4 text-muted-foreground" />
              <Badge variant="secondary" className="text-xs">预留</Badge>
            </div>
            <CardTitle className="text-base">使用统计</CardTitle>
            <CardDescription className="text-xs">
              查看每位用户的研究次数、Token 消耗和活跃度趋势。
            </CardDescription>
          </CardHeader>
        </Card>
      </div>

      <div className="rounded-lg border p-8 text-center text-muted-foreground">
        <Users className="h-12 w-12 mx-auto mb-3 opacity-20" />
        <p className="text-sm">用户管理功能将在后续版本中实现</p>
        <p className="text-xs mt-1">
          当前通过 JWT claims 进行角色识别，无需单独的用户数据库管理。
        </p>
      </div>
    </div>
  );
}
