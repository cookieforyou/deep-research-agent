package com.example.deepresearch.rag;

import com.example.deepresearch.security.PiiMaskingService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 向量存储服务 — 基于 Milvus Java SDK 的向量检索实现.
 * <p>
 * 封装 Milvus 的 Collection 管理、向量检索、文档插入和删除操作。
 * 所有检索强制携带 {@code tenant_id} 过滤条件，实现多租户数据硬隔离。
 * </p>
 *
 * <h3>Collection Schema</h3>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>id (PK)</td><td>Int64</td><td>自增主键</td></tr>
 *   <tr><td>embedding</td><td>FloatVector(1024)</td><td>文本向量（DashScope text-embedding-v3）</td></tr>
 *   <tr><td>text</td><td>VarChar(65535)</td><td>文档文本内容</td></tr>
 *   <tr><td>doc_title</td><td>VarChar(512)</td><td>文档标题</td></tr>
 *   <tr><td>source_url</td><td>VarChar(2048)</td><td>来源 URL</td></tr>
 *   <tr><td>created_at</td><td>VarChar(64)</td><td>文档创建时间</td></tr>
 *   <tr><td>doc_type</td><td>VarChar(32)</td><td>文档类型（pdf/docx/md/txt）</td></tr>
 *   <tr><td>tenant_id</td><td>VarChar(128)</td><td>租户 ID（多租户硬隔离）</td></tr>
 *   <tr><td>session_id</td><td>VarChar(64)</td><td>会话 ID（语义缓存关联）</td></tr>
 *   <tr><td>query</td><td>VarChar(5000)</td><td>原始研究查询（语义缓存关联）</td></tr>
 *   <tr><td>chunk_index</td><td>VarChar(16)</td><td>分块序号</td></tr>
 * </table>
 */
@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final MilvusServiceClient milvusClient;
    private final EmbeddingModel embeddingModel;
    private final PiiMaskingService piiMaskingService;

    /** 向量字段名 */
    private static final String VECTOR_FIELD = "embedding";

    /** 搜索返回字段 */
    private static final List<String> OUT_FIELDS = List.of(
        "text", "doc_title", "source_url", "created_at", "doc_type", "tenant_id",
        "session_id", "query", "chunk_index");

    public VectorStoreService(MilvusServiceClient milvusClient,
                               EmbeddingModel embeddingModel,
                               PiiMaskingService piiMaskingService) {
        this.milvusClient = milvusClient;
        this.embeddingModel = embeddingModel;
        this.piiMaskingService = piiMaskingService;
        // 应用启动时初始化 Collection
        initCollection();
    }

    // =========================== Collection 管理 ===========================

    /**
     * 初始化 Milvus Collection（如果不存在则创建）.
     */
    void initCollection() {
        try {
            String collectionName = MilvusConfig.COLLECTION_NAME;

            // 检查 Collection 是否存在
            R<Boolean> hasCollection = milvusClient.hasCollection(
                HasCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build());

            if (hasCollection.getData() != null && hasCollection.getData()) {
                log.info("Milvus Collection '{}' 已存在，跳过创建", collectionName);
                return;
            }

            // 定义字段 Schema
            List<FieldType> fields = new ArrayList<>();

            // 主键（自增 Int64）
            fields.add(FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(true)
                .build());

            // 向量字段（1024 维 FloatVector，COSINE 相似度）
            fields.add(FieldType.newBuilder()
                .withName(VECTOR_FIELD)
                .withDataType(DataType.FloatVector)
                .withDimension(MilvusConfig.EMBEDDING_DIM)
                .build());

            // 文本内容
            fields.add(FieldType.newBuilder()
                .withName("text")
                .withDataType(DataType.VarChar)
                .withMaxLength(65535)
                .build());

            // 元数据字段
            fields.add(FieldType.newBuilder()
                .withName("doc_title")
                .withDataType(DataType.VarChar)
                .withMaxLength(512)
                .build());

            fields.add(FieldType.newBuilder()
                .withName("source_url")
                .withDataType(DataType.VarChar)
                .withMaxLength(2048)
                .build());

            fields.add(FieldType.newBuilder()
                .withName("created_at")
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .build());

            fields.add(FieldType.newBuilder()
                .withName("doc_type")
                .withDataType(DataType.VarChar)
                .withMaxLength(32)
                .build());

            fields.add(FieldType.newBuilder()
                .withName("tenant_id")
                .withDataType(DataType.VarChar)
                .withMaxLength(128)
                .build());

            // 语义缓存关联字段
            fields.add(FieldType.newBuilder()
                .withName("session_id")
                .withDataType(DataType.VarChar)
                .withMaxLength(64)
                .build());

            fields.add(FieldType.newBuilder()
                .withName("query")
                .withDataType(DataType.VarChar)
                .withMaxLength(5000)
                .build());

            fields.add(FieldType.newBuilder()
                .withName("chunk_index")
                .withDataType(DataType.VarChar)
                .withMaxLength(16)
                .build());

            // 创建 Collection
            R<io.milvus.param.RpcStatus> createResult = milvusClient.createCollection(
                CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldTypes(fields)
                    .build());

            log.info("Milvus Collection '{}' 创建结果: {}", collectionName,
                createResult.getStatus());

            // 创建 IVF_FLAT 索引（向量字段）
            R<io.milvus.param.RpcStatus> indexResult = milvusClient.createIndex(
                CreateIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName(VECTOR_FIELD)
                    .withIndexType(MilvusConfig.INDEX_TYPE)
                    .withMetricType(MilvusConfig.METRIC_TYPE)
                    .withExtraParam("{\"nlist\": 128}")
                    .build());

            log.info("Milvus 索引创建结果: {}", indexResult.getStatus());

            // 加载 Collection 到内存
            milvusClient.loadCollection(
                io.milvus.param.collection.LoadCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build());

            log.info("Milvus Collection '{}' 初始化完成 (dim={}, index={})",
                collectionName, MilvusConfig.EMBEDDING_DIM, MilvusConfig.INDEX_TYPE);

        } catch (Exception e) {
            log.error("Milvus Collection 初始化失败", e);
        }
    }

    // =========================== 向量检索 ===========================

    /**
     * 向量相似度检索（带多租户隔离）.
     *
     * @param query     查询文本
     * @param tenantId  租户 ID（必须传入，硬过滤）
     * @param topK      返回结果数
     * @param threshold 相似度阈值（COSINE 度量下 0.0 ~ 1.0）
     * @return 相关文档列表
     */
    public List<Document> similaritySearch(String query, String tenantId,
                                            int topK, double threshold) {
        return similaritySearch(query, tenantId, null, topK, threshold);
    }

    /**
     * 向量相似度检索（带多租户隔离 + 额外过滤条件）.
     * <p>
     * 用于语义记忆检索等需要按 doc_type 区分的场景。
     * extraFilter 格式如 {@code doc_type == "research_history"}，
     * 会拼接到 tenant_id 表达式后面。
     * </p>
     *
     * @param query       查询文本
     * @param tenantId    租户 ID（必须传入，硬过滤）
     * @param extraFilter 额外 Milvus 标量过滤表达式（如 doc_type == "xxx"），可为 null
     * @param topK        返回结果数
     * @param threshold   相似度阈值（COSINE 度量下 0.0 ~ 1.0）
     * @return 相关文档列表
     */
    public List<Document> similaritySearch(String query, String tenantId,
                                            String extraFilter, int topK, double threshold) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        log.debug("[VectorStore] 开始检索: query='{}', tenantId={}, extraFilter={}, topK={}",
            piiMaskingService.tokenizeToString(
                query.substring(0, Math.min(50, query.length()))), tenantId, extraFilter, topK);

        try {
            // 步骤 1: 将查询文本向量化
            float[] queryVector = embeddingModel.embed(query);

            // 步骤 2: 构造 Milvus 搜索参数
            String filterExpr = String.format("tenant_id == \"%s\"", tenantId);
            if (extraFilter != null && !extraFilter.isBlank()) {
                filterExpr += " && " + extraFilter;
            }

            SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(MilvusConfig.COLLECTION_NAME)
                .withVectorFieldName(VECTOR_FIELD)
                .withFloatVectors(Collections.singletonList(
                    convertToFloatList(queryVector)))
                .withTopK(topK)
                .withMetricType(MilvusConfig.METRIC_TYPE)
                .withParams("{\"nprobe\": 16}")
                .withExpr(filterExpr)
                .withOutFields(OUT_FIELDS)
                .build();

            // 步骤 3: 执行搜索
            R<SearchResults> response = milvusClient.search(searchParam);

            if (response.getStatus() != 0) {
                log.error("[VectorStore] Milvus 搜索失败: {}", response.getMessage());
                return Collections.emptyList();
            }

            // 步骤 4: 转换为 Spring AI Document 列表
            List<Document> documents = convertToDocuments(response.getData(), threshold);
            log.debug("[VectorStore] 检索完成: {} 条结果（阈值={}）", documents.size(), threshold);

            return documents;

        } catch (Exception e) {
            log.error("[VectorStore] 检索异常: query='{}'",
                piiMaskingService.tokenizeToString(
                    query.substring(0, Math.min(50, query.length()))), e);
            return Collections.emptyList();
        }
    }

    // =========================== 文档插入 ===========================

    /**
     * 批量插入文档到 Milvus.
     *
     * @param documents 文档列表（metadata 需包含 tenant_id 等字段）
     * @param tenantId  租户 ID
     */
    public void insertDocuments(List<Document> documents, String tenantId) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        log.info("[VectorStore] 插入 {} 个文档, tenantId={}", documents.size(), tenantId);

        try {
            // 步骤 1: 逐个向量化
            List<float[]> embeddings = new ArrayList<>();
            for (Document doc : documents) {
                try {
                    embeddings.add(embeddingModel.embed(doc.getText()));
                } catch (Exception e) {
                    log.warn("[VectorStore] 单个文档向量化失败，跳过: {}",
                        doc.getText().length() > 50 ? doc.getText().substring(0, 50) + "..." : doc.getText());
                }
            }

            if (embeddings.isEmpty()) {
                log.warn("[VectorStore] 所有文档向量化均失败，跳过插入");
                return;
            }

            // 步骤 2: 构造插入数据
            List<InsertParam.Field> fields = new ArrayList<>();

            // 向量字段
            List<List<Float>> floatVectors = embeddings.stream()
                .map(this::convertToFloatList)
                .toList();
            fields.add(new InsertParam.Field(VECTOR_FIELD, floatVectors));

            // 文本字段
            List<String> textValues = documents.stream()
                .map(d -> d.getText().length() > 65535
                    ? d.getText().substring(0, 65535) : d.getText())
                .toList();
            fields.add(new InsertParam.Field("text", textValues));

            // 元数据字段
            fields.add(new InsertParam.Field("doc_title",
                documents.stream()
                    .map(d -> d.getMetadata().getOrDefault("doc_title", "").toString())
                    .toList()));
            fields.add(new InsertParam.Field("source_url",
                documents.stream()
                    .map(d -> d.getMetadata().getOrDefault("source_url", "").toString())
                    .toList()));
            fields.add(new InsertParam.Field("created_at",
                documents.stream()
                    .map(d -> d.getMetadata().getOrDefault("created_at",
                        java.time.LocalDateTime.now().toString()).toString())
                    .toList()));
            fields.add(new InsertParam.Field("doc_type",
                documents.stream()
                    .map(d -> d.getMetadata().getOrDefault("doc_type", "unknown").toString())
                    .toList()));
            fields.add(new InsertParam.Field("tenant_id",
                Collections.nCopies(documents.size(), tenantId)));
            fields.add(new InsertParam.Field("session_id",
                documents.stream()
                    .map(d -> d.getMetadata().getOrDefault("session_id", "").toString())
                    .toList()));
            fields.add(new InsertParam.Field("query",
                documents.stream()
                    .map(d -> d.getMetadata().getOrDefault("query", "").toString())
                    .toList()));
            fields.add(new InsertParam.Field("chunk_index",
                documents.stream()
                    .map(d -> d.getMetadata().getOrDefault("chunk_index", "").toString())
                    .toList()));

            // 步骤 3: 执行插入
            R<io.milvus.grpc.MutationResult> result = milvusClient.insert(
                InsertParam.newBuilder()
                    .withCollectionName(MilvusConfig.COLLECTION_NAME)
                    .withFields(fields)
                    .build());

            if (result.getStatus() == 0) {
                log.info("[VectorStore] 插入成功: {} 个文档", documents.size());
                // 刷新确保数据持久化
                milvusClient.flush(io.milvus.param.collection.FlushParam.newBuilder()
                    .addCollectionName(MilvusConfig.COLLECTION_NAME)
                    .build());
            } else {
                log.error("[VectorStore] 插入失败: {}", result.getMessage());
            }

        } catch (Exception e) {
            log.error("[VectorStore] 插入异常, tenantId={}", tenantId, e);
        }
    }

    // =========================== 文档删除 ===========================

    /**
     * 按文档 ID 删除（带租户隔离确保安全）.
     */
    public void deleteDocuments(List<String> docIds, String tenantId) {
        if (docIds == null || docIds.isEmpty()) {
            return;
        }

        String ids = docIds.stream()
            .map(id -> "\"" + id + "\"")
            .reduce((a, b) -> a + ", " + b)
            .orElse("");

        String expr = String.format("doc_title in [%s] and tenant_id == \"%s\"",
            ids, tenantId);

        try {
            R<io.milvus.grpc.MutationResult> result = milvusClient.delete(
                io.milvus.param.dml.DeleteParam.newBuilder()
                    .withCollectionName(MilvusConfig.COLLECTION_NAME)
                    .withExpr(expr)
                    .build());

            log.info("[VectorStore] 删除请求完成: expr={}, status={}",
                expr, result.getStatus());

        } catch (Exception e) {
            log.error("[VectorStore] 删除异常", e);
        }
    }

    // =========================== 私有辅助方法 ===========================

    /**
     * 将 Milvus SearchResults 转换为 Spring AI Document 列表.
     */
    private List<Document> convertToDocuments(SearchResults results, double threshold) {
        List<Document> documents = new ArrayList<>();

        if (results == null || results.getResults() == null) {
            return documents;
        }

        int scoreCount = results.getResults().getScoresCount();

        for (int i = 0; i < scoreCount; i++) {
            float score = results.getResults().getScores(i);

            // 相似度阈值过滤
            if (score < threshold) {
                continue;
            }

            // 提取各字段值
            String text = getFieldValue(results, "text", i);
            String docTitle = getFieldValue(results, "doc_title", i);
            String sourceUrl = getFieldValue(results, "source_url", i);
            String createdAt = getFieldValue(results, "created_at", i);
            String docType = getFieldValue(results, "doc_type", i);
            String tenantId = getFieldValue(results, "tenant_id", i);
            String sessionId = getFieldValue(results, "session_id", i);
            String query = getFieldValue(results, "query", i);
            String chunkIndex = getFieldValue(results, "chunk_index", i);

            // 构造 Document
            Document doc = new Document(text != null ? text : "");
            doc.getMetadata().put("score", score);
            doc.getMetadata().put("doc_title", docTitle);
            doc.getMetadata().put("source_url", sourceUrl);
            doc.getMetadata().put("created_at", createdAt);
            doc.getMetadata().put("doc_type", docType);
            doc.getMetadata().put("tenant_id", tenantId);
            doc.getMetadata().put("session_id", sessionId);
            doc.getMetadata().put("query", query);
            doc.getMetadata().put("chunk_index", chunkIndex);

            documents.add(doc);
        }

        return documents;
    }

    /**
     * 从搜索结果获取指定字段的值.
     */
    private String getFieldValue(SearchResults results, String fieldName, int index) {
        try {
            for (var fieldData : results.getResults().getFieldsDataList()) {
                if (fieldData.getFieldName().equals(fieldName)) {
                    if (fieldData.hasScalars() && fieldData.getScalars().hasStringData()) {
                        var data = fieldData.getScalars().getStringData().getDataList();
                        if (index < data.size()) {
                            return data.get(index);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 忽略字段提取错误
        }
        return "";
    }

    /**
     * 将 float[] 转换为 List<Float>（Milvus SDK 要求）.
     */
    private List<Float> convertToFloatList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float v : array) {
            list.add(v);
        }
        return list;
    }

}
