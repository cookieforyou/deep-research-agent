// =========================== 后端 DTO 精确对齐 ===========================
// 所有类型与 Spring Boot 后端 Java Record/DTO 字段一一对应
// 后端源码: src/main/java/com/example/deepresearch/api/dto/

/** POST /api/research 请求体 (ResearchRequest.java) */
export interface ResearchRequest {
  /** 研究查询，1-5000 字符 */
  query: string;
  /** 用户标识（默认 "anonymous"） */
  userId: string;
  /** 租户标识（默认 "default"） */
  tenantId: string;
  /** true=深度研究（多 Agent 流程），false=直接回答 */
  deepResearch: boolean;
}

/** POST /api/research 响应体 (ResearchResponse.java) */
export interface ResearchResponse {
  /** 会话 ID，用于 SSE 流订阅和状态轮询 */
  sessionId: string;
  /** 研究状态 */
  status: 'IN_PROGRESS' | 'COMPLETED' | 'ERROR';
  /** 最终报告（仅 COMPLETED 时有值） */
  report?: string;
  /** 错误信息（仅 ERROR 时有值） */
  error?: string;
  /** 元数据 */
  metadata?: ResearchMetadata;
}

/** 研究元数据 (ResearchResponse.Metadata record) */
export interface ResearchMetadata {
  wordCount: number;
  citationCount: number;
  iterationCount: number;
}

// =========================== SSE 进度事件 ===========================
// 后端: ProgressEvent.java, ResearchStage enum
// SSE 传输格式: event: <stage.lowercase()>, data: <ProgressEvent JSON>

/**
 * 研究阶段枚举 — 精确对齐后端 ResearchStage enum 的 13 个值
 * 后端源码: api/dto/ProgressEvent.java
 */
export type ResearchStage =
  | 'INTENT_ROUTING'
  | 'PLANNING'
  | 'WEB_SEARCHING'
  | 'LOCAL_SEARCHING'
  | 'JUDGING'
  | 'ANALYZING'
  | 'REFLECTING'
  | 'WRITING'
  | 'COMPLETED'
  | 'CACHE_HIT'
  | 'MODEL_FALLBACK'
  | 'SEARCH_FALLBACK'
  | 'ERROR';

/** SSE 进度事件 (ProgressEvent.java) */
export interface ProgressEvent {
  /** 会话 ID */
  sessionId: string;
  /** 当前研究阶段 */
  stage: ResearchStage;
  /** 节点名称（如 "plan", "web_search", "write" 等） */
  nodeName: string;
  /** 完成百分比 (0.0 ~ 100.0) */
  percent: number;
  /** 人类可读的进度描述 */
  message: string;
  /** ISO 8601 时间戳（后端格式: yyyy-MM-dd'T'HH:mm:ss.SSSZ） */
  timestamp: string;
}

// =========================== 领域模型 ===========================
// 与后端 Java Record 对齐

/** 证据/引用 (Evidence.java) */
export interface Evidence {
  sourceId: string;
  sourceType: 'WEB' | 'LOCAL';
  url: string;
  title: string;
  content: string;
  score: number;
  relevanceRank: number;
  domain: string;
  retrievedAt: string;
}

/** 研究发现 (Finding.java) */
export interface Finding {
  findingId: string;
  subQuestionId: string;
  conclusion: string;
  reasoning: string;
  supportingEvidenceIds: string[];
  confidence: number;
}

/** 搜索计划 (SearchPlan.java) */
export interface SearchPlan {
  queryId: string;
  query: string;
  rationale: string;
  priority: number;
}

// =========================== 评估结果 ===========================
// 后端: EvalResult.java（5 维度评分，各 1.0 ~ 5.0）

/** LLM 质量评估结果 */
export interface EvalResult {
  /** 相关性 (1.0 ~ 5.0) */
  relevance: number;
  /** 连贯性 (1.0 ~ 5.0) */
  coherence: number;
  /** 引用准确性 (1.0 ~ 5.0) */
  citationAccuracy: number;
  /** 完备性 (1.0 ~ 5.0) */
  completeness: number;
  /** 简洁性 (1.0 ~ 5.0) */
  conciseness: number;
  /** 综合评分（五维均值） */
  overallScore: number;
  /** 评估摘要 */
  summary: string;
}

// =========================== 研究历史 ===========================
// 后端: ResearchHistory JPA Entity + Projection（Phase 4+ 新增 API）

/** 研究历史列表项（不含报告全文） */
export interface ResearchHistoryItem {
  id: number;
  sessionId: string;
  userId: string;
  tenantId: string;
  query: string;
  /** 列表查询不返回全文，详情接口才返回 */
  report?: string;
  wordCount: number;
  citationCount: number;
  iterationCount: number;
  status: 'COMPLETED' | 'ERROR';
  /** JSON string — EvalResult 序列化，nullable */
  evalScores?: string;
  createdAt: string;
}

/** 分页响应 */
/** Spring Data Page 序列化格式（PageJacksonModule 自动注册） */
export interface PaginatedResponse<T> {
  content: T[];
  /** Spring Data 使用 "number" 字段名（非 "page"） */
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  /** Spring Data 使用 "last" 字段（true = 最后一页） */
  last: boolean;
  first: boolean;
  empty: boolean;
}

// =========================== RFC 7807 错误 ===========================
// 后端: GlobalExceptionHandler 返回 ProblemDetail

/** RFC 7807 Problem Detail */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  timestamp?: string;
  errorCode?: string;
  agent?: string;
  errors?: string[];
}

// =========================== 用户画像 ===========================
// 后端: UserProfile JPA Entity（Phase 5 新增 API）

/** 用户画像 */
export interface UserProfile {
  id: number;
  userId: string;
  tenantId: string;
  /** JSON array string */
  interests: string;
  /** JSON array string */
  recentTopics: string;
  /** JSON map string */
  preferences: string;
  researchCount: number;
  createdAt: string;
  updatedAt: string;
}

// =========================== Prompt 模板管理 ===========================
// 后端: PromptTemplateEntity JPA Entity（Phase 6 新增 API）

/** Prompt 模板 */
export interface PromptTemplate {
  /** 模板标识，如 "intent-router", "planner" */
  id: string;
  /** 乐观锁版本号，每次保存自动 +1 */
  version: number;
  /** StringTemplate 模板内容 */
  content: string;
  /** 状态 */
  status: 'active' | 'inactive' | 'deprecated';
  /** A/B 测试分组 */
  abGroup: 'A' | 'B' | null;
  createdAt: string;
  updatedAt: string;
}

// =========================== 前端专用类型 ===========================

/** SSE 连接状态 */
export type SseConnectionStatus =
  | 'idle'
  | 'connecting'
  | 'connected'
  | 'disconnected'
  | 'error';

/** 研究模式 */
export type ResearchMode = 'deep' | 'direct';

/** API 客户端错误类 */
export class ApiError extends Error {
  constructor(
    public status: number,
    public detail: ProblemDetail,
  ) {
    super(detail.detail || `HTTP ${status}`);
    this.name = 'ApiError';
  }
}
