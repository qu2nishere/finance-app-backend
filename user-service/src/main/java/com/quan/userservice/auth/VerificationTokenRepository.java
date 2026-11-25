package com.quan.userservice.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from VerificationToken t
            where t.user.id = :userId
              and t.purpose = :purpose
              and t.consumedAt is null
            order by t.createdAt desc
            limit 1
            """)
    Optional<VerificationToken> findLatestUnconsumedForUpdate(
            @Param("userId") UUID userId,
            @Param("purpose") VerificationToken.Purpose purpose);

    List<VerificationToken> findByUserIdAndPurposeAndConsumedAtIsNull(
            UUID userId,
            VerificationToken.Purpose purpose);

    long countByUserIdAndPurposeAndCreatedAtAfter(
            UUID userId,
            VerificationToken.Purpose purpose,
            Instant createdAfter);
}
