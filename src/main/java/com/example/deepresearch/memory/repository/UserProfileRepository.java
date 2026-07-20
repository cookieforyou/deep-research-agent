package com.example.deepresearch.memory.repository;

import com.example.deepresearch.memory.entity.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户画像 Repository.
 */
@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    /**
     * 按用户 ID 和租户 ID 查找画像.
     */
    Optional<UserProfile> findByUserIdAndTenantId(String userId, String tenantId);

    /**
     * 按 userId 或 tenantId 模糊搜索，分页返回。
     */
    Page<UserProfile> findByUserIdContainingIgnoreCaseOrTenantIdContainingIgnoreCase(
        String userId, String tenantId, Pageable pageable);
}
