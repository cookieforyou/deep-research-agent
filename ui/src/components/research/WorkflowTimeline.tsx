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
  /** 显示标签 */
  label: string;
  /** lucide-react 图标 */
  Icon: LucideIcon;
  /** 后端 stage（用于匹配事件），null 表示组合节点 */
  stage: ResearchStage | null;
  /** 子 stage（仅 DUAL_SEARCH 使用） */
  children?: { stage: ResearchStage; label: string }[];
  /** 颜色 */
  color: string;
}

interface ComputedNode {
  label: string;
  icon: React.ReactNode;
  status: NodeStatus;
  message?: string;
  elapsed?: number;
  color: string;
  isLast: boolean;
  /** dual_search 子进度 */
  children?: React.ReactNode;
}

// =========================== Timeline 定义 ===========================

const TIMELINE_DEF: TimelineNodeDef[] = [
  {
    label: STAGE_LABELS.INTENT_ROUTING,
    Icon: GitBranch,
    stage: 'INTENT_ROUTING',
    color: STAGE_COLORS.INTENT_ROUTING,
  },
  {
    label: STAGE_LABELS.PLANNING,
    Icon: ClipboardList,
    stage: 'PLANNING',
    color: STAGE_COLORS.PLANNING,
  },
  {
    // dual_search: Web + Local 并行
    label: '双源检索',
    Icon: Globe,
    stage: null, // 组合节点
    color: STAGE_COLORS.WEB_SEARCHING,
    children: [
      { stage: 'WEB_SEARCHING', label: 'Web 搜索' },
      { stage: 'LOCAL_SEARCHING', label: 'Local 检索' },
    ],
  },
  {
    label: STAGE_LABELS.JUDGING,
    Icon: Scale,
    stage: 'JUDGING',
    color: STAGE_COLORS.JUDGING,
  },
  {
    label: STAGE_LABELS.ANALYZING,
    Icon: Brain,
    stage: 'ANALYZING',
    color: STAGE_COLORS.ANALYZING,
  },
  {
    label: STAGE_LABELS.WRITING,
    Icon: PenLine,
    stage: 'WRITING',
    color: STAGE_COLORS.WRITING,
  },
];

// =========================== 工具函数 ===========================

/** 从 ISO 8601 时间戳计算毫秒差 */
function diffMs(start: string, end: string): number {
  return new Date(end).getTime() - new Date(start).getTime();
}

/** 从搜索 ProgressEvent 的 message 中提取进度信息 */
function parseSearchProgress(
  message: string,
): { current: number; total: number } | null {
  // 后端 message 格式: "搜索中: 4/6" 或 "已完成 Web 搜索: 6/6"
  const match = message.match(/(\d+)\s*\/\s*(\d+)/);
  if (match) {
    return { current: parseInt(match[1]), total: parseInt(match[2]) };
  }
  return null;
}

// =========================== 组件 ===========================

interface WorkflowTimelineProps {
  events: ProgressEvent[];
}

/**
 * 工作流时间线组件
 *
 * 根据 SSE 进度事件计算 7 阶段节点状态，渲染纵向时间线。
 *
 * 节点状态计算:
 *   1. 按 WORKFLOW_NODE_ORDER 顺序排列
 *   2. 找到最后一个已开始但未结束的阶段 → active
 *   3. 前面阶段 → done，后面阶段 → pending
 *   4. COMPLETED 事件 → 全部 done
 *   5. ERROR 事件 → 已开始的标记 error
 *
 * 特殊处理:
 *   - dual_search: WEB_SEARCHING + LOCAL_SEARCHING 合并为一个节点
 *   - CACHE_HIT: 不渲染 Timeline（父组件控制）
 *   - MODEL_FALLBACK / SEARCH_FALLBACK: 触发 toast 通知
 */
export function WorkflowTimeline({ events }: WorkflowTimelineProps) {
  // 实时计时器：每 1 秒 tick 一次，更新活跃节点的耗时显示
  const [tick, setTick] = useState(0);

  useEffect(() => {
    const hasActive = events.some((e) => {
      const isTerminal = e.stage === 'COMPLETED' || e.stage === 'ERROR' || e.stage === 'CACHE_HIT';
      return !isTerminal;
    });
    if (!hasActive) return;

    const timer = setInterval(() => {
      setTick((t) => t + 1);
    }, 1000);
    return () => clearInterval(timer);
  }, [events]);

  // 降级通知（副作用）
  useMemo(() => {
    const fallbackEvents = events.filter(
      (e) => e.stage === 'MODEL_FALLBACK' || e.stage === 'SEARCH_FALLBACK',
    );
    // 只通知最新的降级事件
    if (fallbackEvents.length > 0) {
      const latest = fallbackEvents[fallbackEvents.length - 1];
      if (latest.stage === 'MODEL_FALLBACK') {
        toast.warning('模型已降级至 Flash', { id: 'model-fallback' });
      } else {
        toast.warning('搜索已降级至备用引擎', { id: 'search-fallback' });
      }
    }
  }, [events]);

  // 计算节点状态
  const computedNodes = useMemo<ComputedNode[]>(() => {
    // 1. 按 stage 分组事件
    const stageEvents = new Map<ResearchStage, ProgressEvent[]>();
    for (const e of events) {
      const arr = stageEvents.get(e.stage) || [];
      arr.push(e);
      stageEvents.set(e.stage, arr);
    }

    const isCompleted = events.some((e) => e.stage === 'COMPLETED');
    const isError = events.some((e) => e.stage === 'ERROR');

    // 2. 找到当前活跃阶段
    // 方法：从后往前找第一个非终端、非降级的主工作流阶段
    const mainStages = new Set<ResearchStage>([
      'INTENT_ROUTING', 'PLANNING', 'WEB_SEARCHING', 'LOCAL_SEARCHING',
      'JUDGING', 'ANALYZING', 'WRITING',
    ]);

    let activeStage: ResearchStage | null = null;
    if (!isCompleted && !isError) {
      for (let i = events.length - 1; i >= 0; i--) {
        if (mainStages.has(events[i].stage)) {
          activeStage = events[i].stage;
          break;
        }
      }
    }

    // 3. 构建节点列表
    let allDone = isCompleted;
    const nodes: ComputedNode[] = [];

    for (let idx = 0; idx < TIMELINE_DEF.length; idx++) {
      const def = TIMELINE_DEF[idx];
      const isLast = idx === TIMELINE_DEF.length - 1;

      // 确定该节点的 stage 集合
      const stageKeys: ResearchStage[] = def.stage
        ? [def.stage]
        : (def.children?.map((c) => c.stage) || []);

      // 收集事件
      const nodeEvents = stageKeys.flatMap((s) => stageEvents.get(s) || []);

      // 判断状态
      let status: NodeStatus = 'pending';

      if (isError && nodeEvents.length > 0) {
        // 错误后，已开始的节点标记为 error
        status = 'error';
      } else if (allDone) {
        status = 'done';
      } else if (activeStage && stageKeys.includes(activeStage)) {
        status = 'active';
        allDone = false; // 活跃节点之后都是 pending
      } else if (nodeEvents.length > 0 && activeStage === null) {
        status = 'done';
      } else if (nodeEvents.length === 0 && activeStage === null) {
        status = 'pending';
      } else if (
        nodeEvents.length > 0 &&
        activeStage &&
        !stageKeys.includes(activeStage)
      ) {
        // 判断是否在活跃阶段之前
        const activeIdx = TIMELINE_DEF.findIndex(
          (d) =>
            d.stage === activeStage ||
            d.children?.some((c) => c.stage === activeStage),
        );
        const thisIdx = idx;
        status = activeIdx === -1 || thisIdx < activeIdx ? 'done' : 'pending';
      } else {
        status = 'pending';
      }

      // 计算耗时
      let elapsed: number | undefined;
      if (nodeEvents.length > 0 && status !== 'pending') {
        const first = nodeEvents[0].timestamp;
        const last = status === 'active' ? new Date().toISOString() : nodeEvents[nodeEvents.length - 1].timestamp;
        elapsed = diffMs(first, last);
      }

      // 生成描述信息
      let message: string | undefined;
      if (status === 'active' && nodeEvents.length > 0) {
        message = nodeEvents[nodeEvents.length - 1].message;
      } else if (status === 'done' && nodeEvents.length > 0) {
        // 使用最后一个事件的消息（通常是完成消息）
        const lastMsg = nodeEvents[nodeEvents.length - 1].message;
        message = lastMsg;
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
          const isChildDone =
            allDone ||
            !!(
              activeStage &&
              TIMELINE_DEF.findIndex(
                (d) =>
                  d.stage === activeStage ||
                  d.children?.some((c) => c.stage === activeStage),
              ) > idx
            );

          // 解析进度
          let current = 0;
          let total = 0;
          if (childEvents.length > 0) {
            const latestMsg = childEvents[childEvents.length - 1].message;
            const progress = parseSearchProgress(latestMsg);
            if (progress) {
              current = progress.current;
              total = progress.total;
            } else {
              // fallback: 使用 percent 推算
              const pct = childEvents[childEvents.length - 1].percent;
              current = Math.round(pct);
              total = 100;
            }
          }

          return (
            <SearchChildNode
              key={child.stage}
              label={child.label}
              current={current}
              total={total}
              isDone={isChildDone}
            />
          );
        });
      }

      nodes.push({
        label: def.label,
        icon: <def.Icon className="h-4 w-4" />,
        status,
        message,
        elapsed,
        color: def.color,
        isLast,
        children,
      });
    }

    // 如果完成，添加完成节点
    if (isCompleted) {
      const completedEvent = events.find((e) => e.stage === 'COMPLETED');
      nodes.push({
        label: '研究完成',
        icon: <CheckCircle className="h-4 w-4" />,
        status: 'done',
        message: completedEvent?.message || '报告已生成',
        color: STAGE_COLORS.COMPLETED,
        isLast: true,
      });
    }

    // 如果出错，添加错误节点
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
    // eslint-disable-next-line react-hooks/exhaustive-deps -- tick 用于实时更新活跃节点耗时
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
