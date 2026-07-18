'use client';

import { RadarChart, Radar, PolarGrid, PolarAngleAxis, ResponsiveContainer } from 'recharts';
import type { ComponentType } from 'react';
import { EVAL_DIMENSIONS } from '@/lib/constants';
import type { EvalResult } from '@/lib/types';

// recharts 3.x 组件返回 ReactNode（含 undefined），React 19 JSX 要求 Element | null
const PAxis = PolarAngleAxis as ComponentType<any>;

interface MiniRadarChartProps {
  evalResult: EvalResult;
  /** 图表尺寸（px） */
  size?: number;
}

/**
 * 迷你雷达图（用于历史列表行内展示）
 *
 * 精简版：无坐标轴标签、无网格线标注，适合小空间内快速对比。
 */
export function MiniRadarChart({ evalResult, size = 64 }: MiniRadarChartProps) {
  const data = EVAL_DIMENSIONS.map((dim) => ({
    dimension: dim.label.slice(0, 2), // 缩写：相关/连贯/引用/完备/简洁
    score: (evalResult as unknown as Record<string, number>)[dim.key] || 0,
  }));

  return (
    <div style={{ width: size, height: size }}>
      <ResponsiveContainer width="100%" height="100%">
        <RadarChart data={data} cx="50%" cy="50%" outerRadius="55%">
          <PolarGrid stroke="rgba(0,0,0,0.1)" strokeWidth={0.5} />
          <PAxis
            dataKey="dimension"
            tick={{ fontSize: 7, fill: 'rgba(0,0,0,0.4)' }}
            tickLine={false}
          />
          <Radar
            name="score"
            dataKey="score"
            stroke="hsl(220, 90%, 56%)"
            fill="hsl(220, 90%, 56%)"
            fillOpacity={0.25}
            strokeWidth={1}
          />
        </RadarChart>
      </ResponsiveContainer>
    </div>
  );
}
