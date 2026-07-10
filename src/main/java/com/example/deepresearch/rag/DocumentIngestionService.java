package com.example.deepresearch.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    public DocumentIngestionService(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * 导入单个文档.
     *
     * @param rawContent 文档原始内容
     * @param fileName   文件名（用于推断格式）
     * @param tenantId   租户 ID（多租户隔离）
     * @return 导入的文档块数量
     */
    public int ingestDocument(String rawContent, String fileName, String tenantId) {
        log.info("[DocumentIngestion] 导入文档: fileName={}, tenantId={}", fileName, tenantId);

        // 步骤 1: 文本分块
        List<String> chunks = chunkText(rawContent, 1000, 200);

        // 步骤 2: 包装为 Spring AI Document
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Document doc = new Document(chunks.get(i));
            doc.getMetadata().put("doc_id", fileName + "_chunk_" + i);
            doc.getMetadata().put("file_name", fileName);
            doc.getMetadata().put("tenant_id", tenantId);
            doc.getMetadata().put("chunk_index", i);
            doc.getMetadata().put("total_chunks", chunks.size());
            documents.add(doc);
        }

        // 步骤 3: 写入向量库
        vectorStoreService.insertDocuments(documents, tenantId);

        log.info("[DocumentIngestion] 导入完成: {} 个分块", documents.size());
        return documents.size();
    }

    /**
     * 简单文本分块.
     * <p>
     * 按 chunkSize 切分，相邻块重叠 overlap 字符以保持语义连续性。
     * 后续可升级为语义分块（按段落/标题边界）。
     * </p>
     *
     * @param text      原始文本
     * @param chunkSize 块大小（字符数）
     * @param overlap   重叠字符数
     * @return 文本块列表
     */
    List<String> chunkText(String text, int chunkSize, int overlap) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());

            // 尽量在段落边界断开（向后查找最近的换行符）
            if (end < text.length()) {
                int breakPoint = text.lastIndexOf("\n\n", end);
                if (breakPoint > start + chunkSize / 2) {
                    end = breakPoint;
                } else {
                    // 回退到句号处
                    breakPoint = text.lastIndexOf("。", end);
                    if (breakPoint > start + chunkSize / 2) {
                        end = breakPoint + 1;
                    }
                }
            }

            chunks.add(text.substring(start, end).trim());
            // 已到达文本末尾，终止循环（避免 start 回退导致死循环）
            if (end >= text.length()) {
                break;
            }
            start = end - overlap;
        }

        return chunks;
    }
}
