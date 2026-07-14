'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/stores/auth-store';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Separator } from '@/components/ui/separator';
import { Key, LogIn } from 'lucide-react';

/**
 * 登录页面。
 *
 * 生产环境：集成 OAuth2 认证（如 Casdoor）。
 * 当前实现：支持手动输入 JWT token 进行认证。
 */
export default function LoginPage() {
  const router = useRouter();
  const { login, isAuthenticated } = useAuthStore();
  const [token, setToken] = useState('');
  const [refreshToken, setRefreshToken] = useState('');
  const [error, setError] = useState('');

  // 已认证则跳转首页
  if (isAuthenticated) {
    router.replace('/');
    return null;
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (!token.trim()) {
      setError('请输入有效的 JWT Token');
      return;
    }

    try {
      // 验证 token 格式（至少包含两个点）
      if (!token.includes('.') || token.split('.').length < 3) {
        setError('Token 格式无效，请确认输入的是完整的 JWT Token');
        return;
      }

      login(token.trim(), refreshToken.trim() || undefined);
      router.replace('/');
    } catch {
      setError('登录失败，请检查 Token 是否有效');
    }
  };

  return (
    <div className="flex min-h-[80vh] items-center justify-center px-4">
      <div className="w-full max-w-md space-y-6">
        <div className="text-center space-y-2">
          <div className="flex justify-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary text-primary-foreground text-lg font-bold">
              DR
            </div>
          </div>
          <h1 className="text-2xl font-bold">登录 DeepResearch</h1>
          <p className="text-sm text-muted-foreground">
            输入您的 JWT Token 以访问深度研究系统
          </p>
        </div>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Token 认证</CardTitle>
            <CardDescription>
              从 OAuth2 Provider（如 Casdoor）获取 JWT Token 后粘贴到下方。
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="token">Access Token</Label>
                <div className="relative">
                  <Key className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                  <Input
                    id="token"
                    placeholder="eyJhbGciOi..."
                    value={token}
                    onChange={(e) => setToken(e.target.value)}
                    className="pl-9 font-mono text-xs"
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="refresh">Refresh Token（可选）</Label>
                <Input
                  id="refresh"
                  placeholder="用于自动续期..."
                  value={refreshToken}
                  onChange={(e) => setRefreshToken(e.target.value)}
                  className="font-mono text-xs"
                />
              </div>

              {error && (
                <p className="text-xs text-destructive bg-destructive/10 rounded-md px-3 py-2">
                  {error}
                </p>
              )}

              <Button type="submit" className="w-full" disabled={!token.trim()}>
                <LogIn className="h-4 w-4 mr-2" />
                登录
              </Button>
            </form>
          </CardContent>
        </Card>

        <Separator />

        <div className="text-center space-y-1">
          <p className="text-xs text-muted-foreground">
            开发模式下无需登录，设置 <code className="bg-muted px-1 rounded">NEXT_PUBLIC_DEV_MODE=true</code> 即可跳过认证。
          </p>
          <p className="text-xs text-muted-foreground">
            生产环境请配置 OAuth2 Provider 实现 SSO 单点登录。
          </p>
        </div>
      </div>
    </div>
  );
}
