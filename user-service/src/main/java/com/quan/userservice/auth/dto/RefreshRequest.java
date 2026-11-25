package com.quan.userservice.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(
    @NotBlank(message = "Thiếu refresh token")
    @Size(max = 512, message = "Refresh token không hợp lệ")
    String refreshToken
) {}
