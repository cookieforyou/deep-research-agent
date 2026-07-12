package com.example.deepresearch.memory.repository;

import com.example.deepresearch.memory.entity.PromptTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Prompt 模板 JPA Repository.
 */
@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplateEntity, String> {

    /**
     * 按模板 ID 和状态查找激活的模板.
     */
    Optional<PromptTemplateEntity> findByIdAndStatus(String id, String status);

    /**
     * 按模板 ID 和 A/B 分组查找.
     */
    Optional<PromptTemplateEntity> findByIdAndAbGroup(String id, String abGroup);
}
