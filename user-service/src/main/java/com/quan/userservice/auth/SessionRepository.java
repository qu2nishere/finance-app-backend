package com.quan.userservice.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    Optional<Session> findByRefreshTokenHash(String refreshTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Session s where s.refreshTokenHash = :refreshTokenHash")
    Optional<Session> findForUpdateByRefreshTokenHash(@Param("refreshTokenHash") String refreshTokenHash);

    List<Session> findByUserIdAndRevokedAtIsNull(UUID userId);
}
