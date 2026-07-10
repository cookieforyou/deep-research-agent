package com.example.deepresearch.memory.repository;

import com.example.deepresearch.memory.entity.ResearchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 研究历史 Repository.
 */
@Repository
public interface ResearchHistoryRepository extends JpaRepository<ResearchHistory, Long> {

    /**
     * 按用户 ID 和租户 ID 查找最近的研究记录.
     */
    List<ResearchHistory> findByUserIdAndTenantIdOrderByCreatedAtDesc(
        String userId, String tenantId, Pageable pageable);

    /**
     * 按会话 ID 查找.
     */
    java.util.Optional<ResearchHistory> findBySessionId(String sessionId);
}
