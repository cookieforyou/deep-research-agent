package com.example.deepresearch.memory.repository;

import com.example.deepresearch.memory.entity.UserProfile;
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
}
