package com.quan.userservice.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// Khoa chinh la user_id (@MapsId) -> findById(userId) co san, khong can viet findByUserId.
public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID>{
}
