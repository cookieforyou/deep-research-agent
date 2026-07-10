package com.example.deepresearch.rag;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 客户端配置.
 * <p>
 * 创建 {@link MilvusServiceClient} Bean，管理 Milvus 连接生命周期。
 * 连接参数通过 {@code application.yml} 中的环境变量注入。
 * </p>
 */
@Configuration
public class MilvusConfig {

    private static final Logger log = LoggerFactory.getLogger(MilvusConfig.class);

    @Value("${spring.ai.vectorstore.milvus.host:localhost}")
    private String host;

    @Value("${spring.ai.vectorstore.milvus.port:19530}")
    private int port;

    @Value("${spring.ai.vectorstore.milvus.username:}")
    private String username;

    @Value("${spring.ai.vectorstore.milvus.password:}")
    private String password;

    @Value("${spring.ai.vectorstore.milvus.database:deep_research}")
    private String database;

    /**
     * 集合名称常量.
     */
    public static final String COLLECTION_NAME = "deep_research_kb";

    /**
     * 向量维度（DashScope text-embedding-v3）.
     */
    public static final int EMBEDDING_DIM = 1024;

    /**
     * 索引类型.
     */
    public static final IndexType INDEX_TYPE = IndexType.IVF_FLAT;

    /**
     * 相似度度量.
     */
    public static final MetricType METRIC_TYPE = MetricType.COSINE;

    /**
     * 创建 MilvusServiceClient Bean.
     * <p>
     * Bean 销毁时由 Spring 自动调用 {@code close()}。
     * </p>
     */
    @Bean(destroyMethod = "close")
    public MilvusServiceClient milvusServiceClient() {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
            .withHost(host)
            .withPort(port)
            .withDatabaseName(database);

        // 如果配置了用户名密码，使用认证连接
        if (username != null && !username.isEmpty()) {
            builder.withAuthorization(username, password);
        }

        ConnectParam connectParam = builder.build();
        log.info("Milvus 连接初始化: host={}:{}, database={}", host, port, database);

        MilvusServiceClient client = new MilvusServiceClient(connectParam);
        log.info("MilvusServiceClient 创建完成");
        return client;
    }
}
