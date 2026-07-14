package com.example.deepresearch.memory.repository;

import com.example.deepresearch.memory.entity.ResearchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 研究历史 Repository.
 */
@Repository
public interface ResearchHistoryRepository
    extends JpaRepository<ResearchHistory, Long>, JpaSpecificationExecutor<ResearchHistory> {

    /**
     * 按用户和租户分页查询，按创建时间倒序.
     */
    Page<ResearchHistory> findByUserIdAndTenantIdOrderByCreatedAtDesc(
        String userId, String tenantId, Pageable pageable);

    /**
     * 按会话 ID 查询.
     */
    Optional<ResearchHistory> findBySessionId(String sessionId);
}
