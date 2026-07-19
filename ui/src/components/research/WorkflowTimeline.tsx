'use client';

import { useMemo, useState, useEffect } from 'react';
import { toast } from 'sonner';
import {
  GitBranch,
  ClipboardList,
  Globe,
  Scale,
  Brain,
  PenLine,
  MessageSquare,
  CheckCircle,
  XCircle,
  type LucideIcon,
} from 'lucide-react';
import { WorkflowNode, SearchChildNode } from './WorkflowNode';
import type { NodeStatus } from './WorkflowNode';
import type { ProgressEvent, ResearchStage } from '@/lib/types';
import { STAGE_LABELS, STAGE_COLORS } from '@/lib/constants';

// =========================== 类型定义 ===========================

interface TimelineNodeDef {
  label: string;
  Icon: LucideIcon;
  /** 匹配事件的 stage 集合 */
  stages: ResearchStage[];
  color: string;
  /** 仅 dual_search 使用：子阶段映射 */
  children?: { stage: ResearchStage; label: string }[];
}

interface ComputedNode {
  label: string;
  icon: React.ReactNode;
  status: NodeStatus;
  message?: string;
  elapsed?: number;
  color: string;
  isLast: boolean;
  children?: React.ReactNode;
}

// =========================== 流程定义 ===========================

/** 研究流程节点（深度研究） */
const RESEARCH_NODES: TimelineNodeDef[] = [
  {
    label: STAGE_LABELS.INTENT_ROUTING,
    Icon: GitBranch,
    stages: ['INTENT_ROUTING'],
    color: STAGE_COLORS.INTENT_ROUTING,
  },
  {
    label: STAGE_LABELS.PLANNING,
    Icon: ClipboardList,
    stages: ['PLANNING'],
    color: STAGE_COLORS.PLANNING,
  },
  {
    label: '双源检索',
    Icon: Globe,
    stages: ['WEB_SEARCHING', 'LOCAL_SEARCHING'],
    color: STAGE_COLORS.WEB_SEARCHING,
    children: [
      { stage: 'WEB_SEARCHING', label: 'Web 搜索' },
      { stage: 'LOCAL_SEARCHING', label: 'Local 检索' },
    ],
  },
  {
    label: STAGE_LABELS.JUDGING,
    Icon: Scale,
    stages: ['JUDGING'],
    color: STAGE_COLORS.JUDGING,
  },
  {
    label: STAGE_LABELS.ANALYZING,
    Icon: Brain,
    stages: ['ANALYZING'],
    color: STAGE_COLORS.ANALYZING,
  },
  {
    label: STAGE_LABELS.WRITING,
    Icon: PenLine,
    stages: ['WRITING'],
    color: STAGE_COLORS.WRITING,
  },
];

/** 直接回答流程节点 */
const DIRECT_NODES: TimelineNodeDef[] = [
  {
    label: STAGE_LABELS.INTENT_ROUTING,
    Icon: GitBranch,
    stages: ['INTENT_ROUTING'],
    color: STAGE_COLORS.INTENT_ROUTING,
  },
  {
    label: '直接回答',
    Icon: MessageSquare,
    stages: ['PLANNING'], // 后端 direct_answer 节点复用 PLANNING stage
    color: STAGE_COLORS.PLANNING,
  },
];

/** 研究流程的特征 stage（有任意一个即判定为研究流程） */
const RESEARCH_SIGNATURE: ResearchStage[] = [
  'WEB_SEARCHING', 'LOCAL_SEARCHING', 'JUDGING', 'ANALYZING', 'WRITING',
];

// =========================== 工具函数 ===========================

function diffMs(start: string, end: string): number {
  return new Date(end).getTime() - new Date(start).getTime();
}

function parseSearchProgress(message: string): { current: number; total: number } | null {
  const match = message.match(/(\d+)\s*\/\s*(\d+)/);
  if (match) {
    return { current: parseInt(match[1]), total: parseInt(match[2]) };
  }
  return null;
}

/**
 * 根据事件判断流程类型。
 * 保守策略：COMPLETED 到达前不下结论，避免「直接回答→任务规划」的闪烁。
 */
function detectFlowType(
  eventStages: Set<ResearchStage>,
  isCompleted: boolean,
): 'research' | 'direct' | 'pending' {
  for (const sig of RESEARCH_SIGNATURE) {
    if (eventStages.has(sig)) return 'research';
  }
  // 只有在 COMPLETED 到达后才敢说是 direct，之前都是 pending
  if (!isCompleted) return 'pending';
  return 'direct';
}

// =========================== 组件 ===========================

interface WorkflowTimelineProps {
  events: ProgressEvent[];
}

/**
 * 工作流时间线组件 — 动态适配研究/直接回答流程。
 *
 * 根据实际收到的 SSE 事件自动选择展示节点：
 *   - 深度研究：意图路由 → 任务规划 → 双源检索 → 证据过滤 → 分析归纳 → 报告撰写 → 完成
 *   - 直接回答：意图路由 → 直接回答 → 完成
 */
export function WorkflowTimeline({ events }: WorkflowTimelineProps) {
  const [tick, setTick] = useState(0);

  useEffect(() => {
    const hasActive = events.some((e) => {
      const isTerminal = e.stage === 'COMPLETED' || e.stage === 'ERROR' || e.stage === 'CACHE_HIT';
      return !isTerminal;
    });
    if (!hasActive) return;

    const timer = setInterval(() => setTick((t) => t + 1), 1000);
    return () => clearInterval(timer);
  }, [events]);

  // 降级通知
  useMemo(() => {
    const fallbackEvents = events.filter(
      (e) => e.stage === 'MODEL_FALLBACK' || e.stage === 'SEARCH_FALLBACK',
    );
    if (fallbackEvents.length > 0) {
      const latest = fallbackEvents[fallbackEvents.length - 1];
      if (latest.stage === 'MODEL_FALLBACK') {
        toast.warning('模型已降级至 Flash', { id: 'model-fallback' });
      } else {
        toast.warning('搜索已降级至备用引擎', { id: 'search-fallback' });
      }
    }
  }, [events]);

  // === 核心：动态选择节点定义 + 计算状态 ===
  const computedNodes = useMemo<ComputedNode[]>(() => {
    // 1. 按 stage 分组事件
    const stageEvents = new Map<ResearchStage, ProgressEvent[]>();
    const eventStageSet = new Set<ResearchStage>();
    for (const e of events) {
      eventStageSet.add(e.stage);
      const arr = stageEvents.get(e.stage) || [];
      arr.push(e);
      stageEvents.set(e.stage, arr);
    }

    // 2. 状态标志
    const isCompleted = events.some((e) => e.stage === 'COMPLETED');
    const isError = events.some((e) => e.stage === 'ERROR');

    // 3. 判断流程类型，选择节点定义
    const flowType = detectFlowType(eventStageSet, isCompleted);
    const nodeDefs = flowType === 'research' ? RESEARCH_NODES : DIRECT_NODES;

    // 3. 找当前活跃阶段
    const allStages = new Set(nodeDefs.flatMap((d) => d.stages));
    let activeStage: ResearchStage | null = null;
    if (!isCompleted && !isError) {
      for (let i = events.length - 1; i >= 0; i--) {
        if (allStages.has(events[i].stage)) {
          activeStage = events[i].stage;
          break;
        }
      }
    }

    // 4. 构建节点
    let allDone = isCompleted;
    const nodes: ComputedNode[] = [];

    for (let idx = 0; idx < nodeDefs.length; idx++) {
      const def = nodeDefs[idx];
      const isLast = idx === nodeDefs.length - 1 && !isCompleted && !isError;

      const nodeEvents = def.stages.flatMap((s) => stageEvents.get(s) || []);

      // 状态判定
      let status: NodeStatus = 'pending';
      if (isError && nodeEvents.length > 0) {
        status = 'error';
      } else if (allDone) {
        status = 'done';
      } else if (activeStage && def.stages.includes(activeStage)) {
        status = 'active';
        allDone = false;
      } else if (nodeEvents.length > 0 && activeStage === null) {
        status = 'done';
      } else if (nodeEvents.length > 0 && activeStage && !def.stages.includes(activeStage)) {
        const activeIdx = nodeDefs.findIndex((d) => d.stages.includes(activeStage!));
        status = activeIdx === -1 || idx < activeIdx ? 'done' : 'pending';
      }

      // 耗时（需先按时间排序：flatMap 合并多阶段事件时不保证时间顺序）
      let elapsed: number | undefined;
      if (nodeEvents.length > 0 && status !== 'pending') {
        const sorted = [...nodeEvents].sort(
          (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime(),
        );
        const first = sorted[0].timestamp;
        const last = status === 'active'
          ? new Date().toISOString()
          : sorted[sorted.length - 1].timestamp;
        elapsed = diffMs(first, last);
      }

      // 描述
      let message: string | undefined;
      if (status === 'active' && nodeEvents.length > 0) {
        message = nodeEvents[nodeEvents.length - 1].message;
      } else if (status === 'done' && nodeEvents.length > 0) {
        // 完成节点：显示最后一个 100% 事件的完成消息
        const doneEvent = [...nodeEvents].reverse().find(e => e.percent >= 100);
        message = doneEvent?.message || nodeEvents[nodeEvents.length - 1].message;
        // 将 "正在检索..." 替换为 "已完成检索"
        if (def.stages.some(s => s === 'WEB_SEARCHING' || s === 'LOCAL_SEARCHING')) {
          message = (message || '').replace(/正在检索.*$/, '检索完成');
        }
      } else if (status === 'pending') {
        message = '等待中...';
      } else if (status === 'error') {
        message = '因错误中断';
      }

      // dual_search 子节点
      let children: React.ReactNode | undefined;
      if (def.children) {
        children = def.children.map((child) => {
          const childEvents = stageEvents.get(child.stage) || [];
          const childDone = childEvents.length > 0 && (status === 'done' || isCompleted);

          let current = 0, total = 0;
          if (childEvents.length > 0) {
            const latestMsg = childEvents[childEvents.length - 1].message;
            const progress = parseSearchProgress(latestMsg);
            if (progress) { current = progress.current; total = progress.total; }
            else { current = Math.round(childEvents[childEvents.length - 1].percent); total = 100; }
          }

          return (
            <SearchChildNode
              key={child.stage}
              label={child.label}
              current={current}
              total={total}
              isDone={childDone}
            />
          );
        });
      }

      // pending 状态下 PLANNING 节点显示 "分析中"（不确定是研究还是直接回答）
      const showLabel = flowType === 'pending' && def.stages.includes('PLANNING')
        ? '分析中'
        : def.label;

      nodes.push({
        label: showLabel,
        icon: <def.Icon className="h-4 w-4" />,
        status,
        message,
        elapsed,
        color: def.color,
        isLast,
        children,
      });
    }

    // 完成/错误终端节点
    if (isCompleted) {
      const completedEvent = events.find((e) => e.stage === 'COMPLETED');
      nodes.push({
        label: flowType === 'direct' ? '回答完成' : '研究完成',
        icon: <CheckCircle className="h-4 w-4" />,
        status: 'done',
        message: completedEvent?.message || (flowType === 'direct' ? '回答已生成' : '报告已生成'),
        color: STAGE_COLORS.COMPLETED,
        isLast: true,
      });
    }

    if (isError) {
      const errorEvent = events.find((e) => e.stage === 'ERROR');
      nodes.push({
        label: '发生错误',
        icon: <XCircle className="h-4 w-4" />,
        status: 'error',
        message: errorEvent?.message || '研究过程中发生未知错误',
        color: STAGE_COLORS.ERROR,
        isLast: true,
      });
    }

    return nodes;
  }, [events, tick]);

  return (
    <div className="space-y-0 px-2">
      {computedNodes.map((node, index) => (
        <WorkflowNode
          key={index}
          icon={node.icon}
          label={node.label}
          status={node.status}
          message={node.message}
          elapsed={node.elapsed}
          color={node.color}
          isLast={node.isLast}
        >
          {node.children}
        </WorkflowNode>
      ))}
    </div>
  );
}
