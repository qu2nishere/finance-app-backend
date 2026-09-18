package com.quan.userservice.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "Email không được để trống")
    @Size(max = 255)
    String email,

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(max = 72, message = "Mật khẩu phải có độ bé hơn 72")
    String password
){  }