package com.quan.userservice.common.security;

import com.quan.userservice.user.User;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "test-secret-chi-dung-trong-test-toi-thieu-32-ky-tu",
            Duration.ofMinutes(15));

    @Test
    void tokenVuaTaoPhaiHopLeVaDocLaiDungUserId() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("quan@gmail.com");

        String token = jwtService.generateAccessToken(user);

        System.out.println("TOKEN = " + token);

        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(user.getId());
    }

    @Test
    void tokenBiSuaPhaiBiTuChoi() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("quan@gmail.com");

        String token = jwtService.generateAccessToken(user);

        char cuoi = token.charAt(token.length() - 1);
        String tokenBiSua = token.substring(0, token.length() - 1) + (cuoi == 'A' ? 'B' : 'A');

        assertThat(jwtService.isTokenValid(tokenBiSua)).isFalse();
        assertThat(jwtService.isTokenValid("chuoi-bay-ba")).isFalse();
        assertThat(jwtService.isTokenValid(null)).isFalse();
    }
}
