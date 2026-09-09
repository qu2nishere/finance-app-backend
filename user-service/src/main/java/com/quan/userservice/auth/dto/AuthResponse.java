package com.quan.userservice.auth.dto;

import java.util.UUID;

public record AuthResponse(
    String accessToken, 
    String tokenType, 
    long expiresIn, 
    UUID userId, 
    String email, 
    String fullName
){   }