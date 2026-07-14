'use client';

import { useState, useEffect } from 'react';

interface NetworkStatus {
  /** 是否在线 */
  online: boolean;
  /** 网络连接类型（如果可用） */
  effectiveType?: string;
}

/**
 * 网络状态检测 Hook
 *
 * 监听浏览器 online/offline 事件和 Network Information API。
 * 用于 SSE 断连后的降级提示。
 */
export function useNetworkStatus(): NetworkStatus {
  const [status, setStatus] = useState<NetworkStatus>({
    online: typeof navigator !== 'undefined' ? navigator.onLine : true,
    effectiveType: undefined,
  });

  useEffect(() => {
    const handleOnline = () => setStatus((s) => ({ ...s, online: true }));
    const handleOffline = () => setStatus((s) => ({ ...s, online: false }));

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    // Network Information API (Chrome/Safari)
    const connection =
      (navigator as Navigator & { connection?: { effectiveType?: string } }).connection;
    if (connection) {
      setStatus((s) => ({ ...s, effectiveType: connection.effectiveType }));
    }

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  return status;
}
