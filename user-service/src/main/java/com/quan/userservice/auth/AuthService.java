package com.quan.userservice.auth;

import com.quan.userservice.auth.dto.AuthResponse;
import com.quan.userservice.auth.dto.RegisterRequest;
import com.quan.userservice.common.security.JwtService;
import com.quan.userservice.settings.NotificationSettings;
import com.quan.userservice.settings.NotificationSettingsRepository;
import com.quan.userservice.settings.UserSettings;
import com.quan.userservice.settings.UserSettingsRepository;
import com.quan.userservice.user.User;
import com.quan.userservice.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService{
    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional 
    public AuthResponse register(RegisterRequest request){
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if(userRepository.existsByEmailAndDeletedAtIsNull(email)){
            throw new EmailAlreadyUsedException(email);
        }

        //Gọi User repo lưu vào bảng User
        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        userRepository.save(user);

        //Lưu cách thức đăng ký vào trong Credential (nơi lưu cách đăng ký bằng email và mật khẩu)
        Credential credential = new Credential();
        credential.setUser(user);
        credential.setPasswordHash(passwordEncoder.encode(request.password()));
        credentialRepository.save(credential);

        //Hai bảng cài đặt, các giá trị default có sẵn
        userSettingsRepository.save(new UserSettings(user));
        notificationSettingsRepository.save(new NotificationSettings(user));

        String accessToken = jwtService.generateAccessToken(user);

        return new AuthResponse(
                accessToken,
                "Bearer",
                jwtService.getAccessTokenTtlSeconds(),
                user.getId(),
                user.getEmail(),
                user.getFullName());
    }
}