import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
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
