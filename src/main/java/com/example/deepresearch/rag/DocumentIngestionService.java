package com.example.deepresearch.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

/**
 * 文档导入服务 — 知识库 ETL 管道.
 * <p>
 * 实现三层渐进式知识库架构的<strong>L3 用户注入层</strong>：
 * 接收用户上传的文档（PDF/Word/Markdown/TXT），
 * 经过分块（chunking）、向量化（embedding）、写入 Milvus 的完整 ETL 流程。
 * </p>
 *
 * <h3>ETL 管道</h3>
 * <ol>
 *   <li><b>Extract</b>: 读取文档内容（支持 PDF/Word/Markdown/TXT）</li>
 *   <li><b>Transform</b>: 文本清洗 + 智能分块（chunk_size=1000, overlap=200）</li>
 *   <li><b>Load</b>: 向量化后写入 Milvus（附带 tenant_id 元数据）</li>
 * </ol>
 *
 * <h3>TODO</h3>
 * <ul>
 *   <li>PDF 解析（Apache PDFBox 或 Tika）</li>
 *   <li>Word 解析（Apache POI）</li>
 *   <li>Markdown 分段（按标题层级智能分割）</li>
 * </ul>
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStoreService vectorStoreService;

    /** Spring AI 2.0 内置 TokenTextSplitter（Builder 模式），替代自定义 chunkText() */
    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
        .withChunkSize(800)
        .withMinChunkSizeChars(200)
        .withMinChunkLengthToEmbed(5)
        .withMaxNumChunks(10000)
        .withKeepSeparator(true)
        .withPunctuationMarks(List.of('\n', '。', '？', '！', '；', '.', '?', '!', ';'))
        .build();

    public DocumentIngestionService(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * 导入文档（文件流版本 — 支持 PDF/Word/HTML/Markdown）.
     * <p>
     * 使用 Spring AI 2.0 的 TikaDocumentReader 自动识别文档格式并解析，
     * 使用 TokenTextSplitter 按语义边界分块。
     * 这是推荐的入口方法。
     * </p>
     *
     * @param input    文档输入流
     * @param fileName 文件名（用于元数据标记和格式推断）
     * @param tenantId 租户 ID（多租户隔离）
     * @return 导入的文档块数量
     */
    public int ingestDocument(InputStream input, String fileName, String tenantId) {
        log.info("[DocumentIngestion] 导入文档（流式）: fileName={}, tenantId={}", fileName, tenantId);

        // 步骤 1: TikaDocumentReader 自动识别并解析文档（包装 InputStream 为 Spring Resource）
        TikaDocumentReader reader = new TikaDocumentReader(new InputStreamResource(input));
        List<Document> docs = reader.get();
        log.debug("[DocumentIngestion] Tika 解析完成: {} 个文档", docs.size());

        // 步骤 2: Spring AI TokenTextSplitter 智能分块
        List<Document> chunks = splitter.apply(docs);

        // 步骤 3: 填充元数据
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            chunk.getMetadata().put("doc_id", fileName + "_chunk_" + i);
            chunk.getMetadata().put("file_name", fileName);
            chunk.getMetadata().put("tenant_id", tenantId);
            chunk.getMetadata().put("chunk_index", i);
            chunk.getMetadata().put("total_chunks", chunks.size());
        }

        // 步骤 4: 写入向量库
        vectorStoreService.insertDocuments(chunks, tenantId);

        log.info("[DocumentIngestion] 导入完成: {} 个分块", chunks.size());
        return chunks.size();
    }

    /**
     * 导入文档（纯文本版本 — 兼容旧接口）.
     *
     * @deprecated 推荐使用 {@link #ingestDocument(InputStream, String, String)} 支持更多格式
     */
    @Deprecated
    public int ingestDocument(String rawContent, String fileName, String tenantId) {
        log.info("[DocumentIngestion] 导入纯文本文档: fileName={}, tenantId={}", fileName, tenantId);

        // 包装为单个 Document
        Document doc = new Document(rawContent);
        doc.getMetadata().put("file_name", fileName);

        // 使用 Spring AI 2.0 TokenTextSplitter 分块
        List<Document> chunks = splitter.apply(List.of(doc));
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            chunk.getMetadata().put("doc_id", fileName + "_chunk_" + i);
            chunk.getMetadata().put("tenant_id", tenantId);
            chunk.getMetadata().put("chunk_index", i);
            chunk.getMetadata().put("total_chunks", chunks.size());
        }

        vectorStoreService.insertDocuments(chunks, tenantId);

        log.info("[DocumentIngestion] 导入完成: {} 个分块", chunks.size());
        return chunks.size();
    }
}
