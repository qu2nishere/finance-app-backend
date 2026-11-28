package com.quan.userservice.common.security;

import com.quan.userservice.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService{
    private final SecretKey key;
    private final Duration accessTokenTtl;

    public JwtService(
        @Value("${app.jwt.secret}") String secret, 
        @Value("${app.jwt.access-token-ttl}") Duration accessTokenTtl){
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            this.accessTokenTtl = accessTokenTtl;
    }

    public String generateAccessToken(User user){
        return generateAccessToken(user, Instant.now().plus(accessTokenTtl));
    }

    public String generateAccessToken(User user, Instant expiresAt) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("tz", user.getTimezone())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public UUID extractUserId(String token){
        return UUID.fromString(verifiedClaims(token).getSubject());
    }

    Claims verifiedClaims(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean isTokenValid(String token){
        try{
            extractUserId(token);
            return true;
        }
        catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getAccessTokenTtlSeconds(){
        return accessTokenTtl.toSeconds();
    }
}
