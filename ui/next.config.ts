import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  // 关闭 Strict Mode：SSE 长连接在双挂载下被反复杀伤，
  // ERR_INCOMPLETE_CHUNKED_ENCODING + FAIL_CANCELLED 无法在应用层修复
  reactStrictMode: false,

  // API 代理到 Spring Boot 后端
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${process.env.BACKEND_URL || 'http://localhost:8080'}/api/:path*`,
      },
      {
        source: '/actuator/:path*',
        destination: `${process.env.BACKEND_URL || 'http://localhost:8080'}/actuator/:path*`,
      },
    ];
  },
};

export default nextConfig;
