package com.quan.userservice.auth;

import com.quan.userservice.auth.dto.AuthResponse;
import com.quan.userservice.auth.dto.RefreshRequest;
import com.quan.userservice.auth.exception.InvalidRefreshTokenException;
import com.quan.userservice.common.util.TokenHasher;
import com.quan.userservice.user.User;
import com.quan.userservice.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RefreshTokenConcurrencyIT {

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    private UUID createdUserId;

    private record Outcome(AuthResponse response, Throwable error) {
    }

    @AfterEach
    void tearDown() {
        if (createdUserId != null) {
            userRepository.deleteById(createdUserId);
            createdUserId = null;
        }
    }

    @Test
    void refresh_twoConcurrentRequestsWithSameToken_onlyOneSucceedsAndRevokesNewSession() throws Exception {
        User user = new User();
        user.setEmail("concurrency-" + UUID.randomUUID() + "@example.com");
        user.setFullName("Concurrency Test");
        userRepository.save(user);
        createdUserId = user.getId();

        String rawToken = refreshTokenService.issue(user);

        CountDownLatch readyGate = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Outcome> callRefresh = () -> {
                readyGate.countDown();
                if (!startGate.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for start gate");
                }
                try {
                    return new Outcome(authService.refresh(new RefreshRequest(rawToken)), null);
                } catch (Throwable t) {
                    return new Outcome(null, t);
                }
            };

            Future<Outcome> first = executor.submit(callRefresh);
            Future<Outcome> second = executor.submit(callRefresh);

            assertThat(readyGate.await(5, TimeUnit.SECONDS)).isTrue();
            startGate.countDown();

            List<Outcome> outcomes = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            List<AuthResponse> successes = outcomes.stream()
                    .map(Outcome::response)
                    .filter(r -> r != null)
                    .toList();
            List<Throwable> errors = outcomes.stream()
                    .map(Outcome::error)
                    .filter(e -> e != null)
                    .toList();

            assertThat(successes).hasSize(1);
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0)).isInstanceOf(InvalidRefreshTokenException.class);

            Session newlyIssued = sessionRepository
                    .findByRefreshTokenHash(TokenHasher.sha256(successes.get(0).refreshToken()))
                    .orElseThrow();
            assertThat(newlyIssued.getRevokedAt()).isNotNull();
            assertThat(newlyIssued.getRevokedReason()).isEqualTo("REUSE_DETECTED");
            assertThat(sessionRepository.findByUserIdAndRevokedAtIsNull(createdUserId)).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }
}
