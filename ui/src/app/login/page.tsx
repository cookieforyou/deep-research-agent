'use client';

import { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/stores/auth-store';
import { loginWithPassword } from '@/lib/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Separator } from '@/components/ui/separator';
import { User, Lock, LogIn, Loader2 } from 'lucide-react';

/**
 * 登录页面。
 *
 * 集成 Casdoor OAuth2 Password Grant 认证。
 * 用户输入用户名和密码，调用授权服务获取 JWT access_token。
 */
export default function LoginPage() {
  const router = useRouter();
  const { login, isAuthenticated } = useAuthStore();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      setError('');

      if (!username.trim()) {
        setError('请输入用户名');
        return;
      }
      if (!password) {
        setError('请输入密码');
        return;
      }

      setLoading(true);
      try {
        const data = await loginWithPassword(username.trim(), password);
        login(data.access_token, data.refresh_token);
        router.replace('/');
      } catch (err) {
        setError(err instanceof Error ? err.message : '登录失败，请检查用户名和密码');
      } finally {
        setLoading(false);
      }
    },
    [username, password, login, router],
  );

  // 已认证则跳转首页（useEffect 中执行，不在 render 期间调用 router）
  useEffect(() => {
    if (isAuthenticated) {
      router.replace('/');
    }
  }, [isAuthenticated, router]);

  // 已认证时不渲染登录表单（等待重定向）
  if (isAuthenticated) {
    return null;
  }

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
            使用您的 Casdoor 账号登录深度研究系统
          </p>
        </div>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">账号登录</CardTitle>
            <CardDescription>
              输入用户名和密码，通过 OAuth2 Password Grant 获取访问令牌。
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="username">用户名</Label>
                <div className="relative">
                  <User className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                  <Input
                    id="username"
                    placeholder="请输入用户名"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    className="pl-9"
                    autoComplete="username"
                    disabled={loading}
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="password">密码</Label>
                <div className="relative">
                  <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                  <Input
                    id="password"
                    type="password"
                    placeholder="请输入密码"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    className="pl-9"
                    autoComplete="current-password"
                    disabled={loading}
                  />
                </div>
              </div>

              {error && (
                <p className="text-xs text-destructive bg-destructive/10 rounded-md px-3 py-2">
                  {error}
                </p>
              )}

              <Button type="submit" className="w-full" disabled={loading}>
                {loading ? (
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                ) : (
                  <LogIn className="h-4 w-4 mr-2" />
                )}
                {loading ? '登录中...' : '登录'}
              </Button>
            </form>
          </CardContent>
        </Card>

        <Separator />

        <div className="text-center space-y-1">
          <p className="text-xs text-muted-foreground">
            由 Casdoor 提供统一身份认证服务
          </p>
        </div>
      </div>
    </div>
  );
}
