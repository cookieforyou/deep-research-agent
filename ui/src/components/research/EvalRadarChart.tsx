'use client';

import {
  RadarChart,
  Radar,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  ResponsiveContainer,
} from 'recharts';
import type { ComponentType } from 'react';
import { EVAL_DIMENSIONS } from '@/lib/constants';
import type { EvalResult } from '@/lib/types';
import { useTheme } from 'next-themes';

// recharts 3.x 组件返回 ReactNode（含 undefined），React 19 JSX 要求 Element | null
// see: https://github.com/recharts/recharts/issues
const PAxis = PolarAngleAxis as ComponentType<any>;
const PRAxis = PolarRadiusAxis as ComponentType<any>;

interface EvalRadarChartProps {
  evalResult: EvalResult;
  height?: number;
}

/**
 * 五维评估雷达图（Recharts）
 *
 * 维度：相关性 / 连贯性 / 引用准确性 / 完备性 / 简洁性
 * 范围：0 ~ 5
 */
export function EvalRadarChart({ evalResult, height = 280 }: EvalRadarChartProps) {
  const { theme } = useTheme();
  const isDark = theme === 'dark';

  const data = EVAL_DIMENSIONS.map((dim) => ({
    dimension: dim.label,
    score: (evalResult as unknown as Record<string, number>)[dim.key] || 0,
    fullMark: 5,
  }));

  return (
    <ResponsiveContainer width="100%" height={height}>
      <RadarChart data={data} cx="50%" cy="50%" outerRadius="70%">
        <PolarGrid stroke={isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)'} />
        <PAxis
          dataKey="dimension"
          tick={{
            fontSize: 11,
            fill: isDark ? 'rgba(255,255,255,0.5)' : 'rgba(0,0,0,0.5)',
          }}
        />
        <PRAxis
          angle={30}
          domain={[0, 5]}
          tick={{ fontSize: 10, fill: 'rgba(0,0,0,0.3)' }}
          axisLine={false}
        />
        <Radar
          name="评分"
          dataKey="score"
          stroke="hsl(220, 90%, 56%)"
          fill="hsl(220, 90%, 56%)"
          fillOpacity={0.3}
          strokeWidth={2}
        />
      </RadarChart>
    </ResponsiveContainer>
  );
}
