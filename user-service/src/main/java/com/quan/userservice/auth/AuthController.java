package com.quan.userservice.auth;

import com.quan.userservice.auth.dto.AuthResponse;
import com.quan.userservice.auth.dto.GoogleLoginRequest;
import com.quan.userservice.auth.dto.LoginRequest;
import com.quan.userservice.auth.dto.RegisterRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/auth", "/api/auth"})
@RequiredArgsConstructor
public class AuthController{
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request){
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request){
        return authService.login(request);
    }

    @PostMapping("/google")
    public AuthResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request){
        return authService.loginWithGoogle(request);
    }
}