import type { ResearchStage } from './types';

// =========================== 研究阶段可视化映射 ===========================

/** 研究阶段 → 中文标签 */
export const STAGE_LABELS: Record<ResearchStage, string> = {
  INTENT_ROUTING: '意图判断',
  PLANNING: '任务规划',
  WEB_SEARCHING: '网络搜索',
  LOCAL_SEARCHING: '本地检索',
  JUDGING: '证据过滤',
  ANALYZING: '分析归纳',
  WRITING: '报告撰写',
  COMPLETED: '研究完成',
  CACHE_HIT: '缓存命中',
  MODEL_FALLBACK: '模型降级',
  SEARCH_FALLBACK: '搜索降级',
  ERROR: '错误',
};

/** 研究阶段 → 图标名称（lucide-react icon key） */
export const STAGE_ICONS: Record<ResearchStage, string> = {
  INTENT_ROUTING: 'GitBranch',
  PLANNING: 'ClipboardList',
  WEB_SEARCHING: 'Globe',
  LOCAL_SEARCHING: 'Database',
  JUDGING: 'Scale',
  ANALYZING: 'Brain',
  WRITING: 'PenLine',
  COMPLETED: 'CheckCircle',
  CACHE_HIT: 'Zap',
  MODEL_FALLBACK: 'AlertTriangle',
  SEARCH_FALLBACK: 'AlertTriangle',
  ERROR: 'XCircle',
};

/** 研究阶段 → Timeline 节点颜色（引用 CSS 变量，自动适配亮/暗主题） */
export const STAGE_COLORS: Record<ResearchStage, string> = {
  INTENT_ROUTING: 'var(--workflow-intent)',
  PLANNING: 'var(--workflow-plan)',
  WEB_SEARCHING: 'var(--workflow-search)',
  LOCAL_SEARCHING: 'var(--workflow-search)',
  JUDGING: 'var(--workflow-filter)',
  ANALYZING: 'var(--workflow-analyze)',
  WRITING: 'var(--workflow-write)',
  COMPLETED: 'var(--workflow-completed)',
  CACHE_HIT: 'var(--workflow-completed)',
  MODEL_FALLBACK: 'var(--workflow-filter)',
  SEARCH_FALLBACK: 'var(--workflow-filter)',
  ERROR: 'var(--workflow-error)',
};

/** 搜索阶段列表（dual_search 节点的子阶段） */
export const SEARCH_STAGES: ResearchStage[] = ['WEB_SEARCHING', 'LOCAL_SEARCHING'];

// =========================== Prompt 模板 ===========================

/** Prompt 模板 ID → 中文名称 */
export const PROMPT_TEMPLATE_NAMES: Record<string, string> = {
  'intent-router': '意图路由',
  'planner': '任务规划',
  'web-scout': '网络搜索',
  'local-scout': '本地检索',
  'analyst': '分析归纳',
  'writer': '报告撰写',
  'direct-answer': '直接回答',
  'eval': '质量评估',
  'preference-extractor': '偏好提取',
};

// =========================== 示例查询 ===========================

/** 首页示例查询 */
export const EXAMPLE_QUERIES = [
  { icon: '🚗', text: '2026年中国新能源汽车市场趋势与竞争格局分析' },
  { icon: '🔬', text: '全球AI芯片产业链格局及国产替代进展' },
  { icon: '☀️', text: '光伏产业N型电池技术路线对比与未来展望' },
  { icon: '🤖', text: '具身智能人形机器人商业化落地前景分析' },
  { icon: '💊', text: '中国创新药出海策略与全球监管对比' },
];

// =========================== SSE 配置 ===========================

/** SSE 重连间隔（毫秒），指数退避 */
export const SSE_RECONNECT_DELAYS = [1000, 2000, 4000, 8000, 16000];

/** SSE 最大重连次数 */
export const SSE_MAX_RECONNECT = Number(process.env.NEXT_PUBLIC_SSE_RECONNECT_MAX) || 5;

/** SSE 心跳超时（毫秒），后端每 15 秒发送心跳 */
export const SSE_HEARTBEAT_TIMEOUT =
  Number(process.env.NEXT_PUBLIC_SSE_HEARTBEAT_TIMEOUT) || 30000;

// =========================== 查询限制 ===========================

/** 最大查询长度 */
export const MAX_QUERY_LENGTH = Number(process.env.NEXT_PUBLIC_MAX_QUERY_LENGTH) || 5000;

// =========================== 评估维度 ===========================

/** 五维评估维度 */
export const EVAL_DIMENSIONS = [
  { key: 'relevance' as const, label: '相关性' },
  { key: 'coherence' as const, label: '连贯性' },
  { key: 'citationAccuracy' as const, label: '引用准确性' },
  { key: 'completeness' as const, label: '完备性' },
  { key: 'conciseness' as const, label: '简洁性' },
];
