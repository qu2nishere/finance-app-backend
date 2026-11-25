package com.quan.userservice.auth;

import com.quan.userservice.auth.exception.InvalidRefreshTokenException;
import com.quan.userservice.common.util.TokenHasher;
import com.quan.userservice.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final SessionRepository sessionRepository;
    private final Duration refreshTokenTtl;

    public RefreshTokenService(
            SessionRepository sessionRepository,
            @Value("${app.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
        this.sessionRepository = sessionRepository;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    /** Cap token moi. Chuoi tho chi ton tai o dang doc duoc tai thoi diem tra ve, DB chi luu SHA-256. */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Session session = new Session();
        session.setUser(user);
        session.setRefreshTokenHash(TokenHasher.sha256(rawToken));
        session.setExpiresAt(Instant.now().plus(refreshTokenTtl));
        sessionRepository.save(session);

        return rawToken;
    }

    /**
     * Doi token tho lay phien hop le va thu hoi ngay token cu (ROTATED).
     * noRollbackFor: khi phat hien tai su dung token da thu hoi, van phai COMMIT viec khoa toan bo phien.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Session verifyAndConsume(String rawToken) {
        // Khoa dong de 2 request refresh song song cung 1 token khong cung doc thay revoked_at = null
        Session session = sessionRepository.findForUpdateByRefreshTokenHash(TokenHasher.sha256(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        Instant now = Instant.now();

        if (session.getRevokedAt() != null) {
            UUID userId = session.getUser().getId();
            log.warn("Refresh token reuse detected for user {}, revoking all active sessions", userId);
            revokeAllForUser(userId, "REUSE_DETECTED");
            throw new InvalidRefreshTokenException();
        }
        if (!session.isActive(now)) {
            throw new InvalidRefreshTokenException();
        }

        session.setLastUsedAt(now);
        session.setRevokedAt(now);
        session.setRevokedReason("ROTATED");
        return session;
    }

    @Transactional
    public void revoke(String rawToken, String reason) {
        sessionRepository.findForUpdateByRefreshTokenHash(TokenHasher.sha256(rawToken))
                .filter(s -> s.getRevokedAt() == null)
                .ifPresent(s -> {
                    s.setRevokedAt(Instant.now());
                    s.setRevokedReason(reason);
                });
    }

    @Transactional
    public void revokeAllForUser(UUID userId, String reason) {
        List<Session> active = sessionRepository.findByUserIdAndRevokedAtIsNull(userId);
        Instant now = Instant.now();
        for (Session s : active) {
            s.setRevokedAt(now);
            s.setRevokedReason(reason);
        }
    }
}
