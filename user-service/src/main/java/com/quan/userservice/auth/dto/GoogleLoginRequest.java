package com.quan.userservice.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
    @NotBlank(message = "Google ID Token không được để trống")
    String idToken,
    @jakarta.validation.constraints.Size(max = 50) String timezone
) {}
