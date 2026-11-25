package com.quan.userservice.auth;

import com.quan.userservice.auth.exception.InvalidRefreshTokenException;
import com.quan.userservice.common.util.TokenHasher;
import com.quan.userservice.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    private RefreshTokenService refreshTokenService;
    private User user;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(sessionRepository, Duration.ofDays(30));
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("quan@gmail.com");
        user.setFullName("Nguyễn Minh");
    }

    @Test
    void issue_savesSha256HashDifferentFromRawToken() {
        String rawToken = refreshTokenService.issue(user);

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());

        Session saved = captor.getValue();
        assertThat(rawToken).isNotBlank();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getRefreshTokenHash())
                .hasSize(64)
                .isEqualTo(TokenHasher.sha256(rawToken))
                .isNotEqualTo(rawToken);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(29)));
        assertThat(saved.getRevokedAt()).isNull();
    }

    @Test
    void verifyAndConsume_withValidToken_marksRotated() {
        String rawToken = "valid-refresh-token";
        Session active = new Session();
        active.setUser(user);
        active.setRefreshTokenHash(TokenHasher.sha256(rawToken));
        active.setExpiresAt(Instant.now().plus(Duration.ofDays(10)));

        when(sessionRepository.findForUpdateByRefreshTokenHash(TokenHasher.sha256(rawToken)))
                .thenReturn(Optional.of(active));

        Session consumed = refreshTokenService.verifyAndConsume(rawToken);

        assertThat(consumed).isSameAs(active);
        assertThat(consumed.getRevokedAt()).isNotNull();
        assertThat(consumed.getLastUsedAt()).isNotNull();
        assertThat(consumed.getRevokedReason()).isEqualTo("ROTATED");
    }

    @Test
    void verifyAndConsume_withRevokedToken_revokesAllUserSessionsAsReuseDetected() {
        String reusedToken = "already-rotated-token";
        Session rotated = new Session();
        rotated.setUser(user);
        rotated.setRefreshTokenHash(TokenHasher.sha256(reusedToken));
        rotated.setExpiresAt(Instant.now().plus(Duration.ofDays(10)));
        rotated.setRevokedAt(Instant.now().minusSeconds(60));
        rotated.setRevokedReason("ROTATED");

        Session activeOther1 = new Session();
        activeOther1.setUser(user);
        activeOther1.setExpiresAt(Instant.now().plus(Duration.ofDays(20)));

        Session activeOther2 = new Session();
        activeOther2.setUser(user);
        activeOther2.setExpiresAt(Instant.now().plus(Duration.ofDays(25)));

        when(sessionRepository.findForUpdateByRefreshTokenHash(TokenHasher.sha256(reusedToken)))
                .thenReturn(Optional.of(rotated));
        when(sessionRepository.findByUserIdAndRevokedAtIsNull(user.getId()))
                .thenReturn(List.of(activeOther1, activeOther2));

        assertThatThrownBy(() -> refreshTokenService.verifyAndConsume(reusedToken))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(activeOther1.getRevokedAt()).isNotNull();
        assertThat(activeOther1.getRevokedReason()).isEqualTo("REUSE_DETECTED");
        assertThat(activeOther2.getRevokedAt()).isNotNull();
        assertThat(activeOther2.getRevokedReason()).isEqualTo("REUSE_DETECTED");
    }

    @Test
    void verifyAndConsume_withExpiredToken_throwsWithoutRevokingAll() {
        String expiredToken = "expired-token";
        Session expired = new Session();
        expired.setUser(user);
        expired.setRefreshTokenHash(TokenHasher.sha256(expiredToken));
        expired.setExpiresAt(Instant.now().minusSeconds(5));

        when(sessionRepository.findForUpdateByRefreshTokenHash(TokenHasher.sha256(expiredToken)))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.verifyAndConsume(expiredToken))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(expired.getRevokedAt()).isNull();
        verify(sessionRepository, never()).findByUserIdAndRevokedAtIsNull(user.getId());
    }

    @Test
    void revoke_marksActiveSessionWithLogoutReason() {
        String rawToken = "active-logout-token";
        Session active = new Session();
        active.setUser(user);
        active.setRefreshTokenHash(TokenHasher.sha256(rawToken));
        active.setExpiresAt(Instant.now().plus(Duration.ofDays(5)));

        when(sessionRepository.findForUpdateByRefreshTokenHash(TokenHasher.sha256(rawToken)))
                .thenReturn(Optional.of(active));

        refreshTokenService.revoke(rawToken, "LOGOUT");

        assertThat(active.getRevokedAt()).isNotNull();
        assertThat(active.getRevokedReason()).isEqualTo("LOGOUT");
    }
}
