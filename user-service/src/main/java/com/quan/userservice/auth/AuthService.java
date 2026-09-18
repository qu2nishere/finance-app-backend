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

import com.quan.userservice.auth.dto.LoginRequest;
import com.quan.userservice.user.UserStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService{
    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

        private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    // Hash that, tinh 1 lan luc nap class. Chi dung de BCrypt ton thoi gian nhu that
    // khi email khong ton tai (chong timing attack). Doi encoder thi doi dong nay theo.
    private static final String DUMMY_HASH = new BCryptPasswordEncoder().encode("khong-ai-dung-mat-khau-nay");

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

        return buildAuthResponse(user);
    }

    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public AuthResponse login(LoginRequest request){
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);
        Credential credential = (user == null) ? null
                : credentialRepository.findByUserId(user.getId()).orElse(null);

        // Khong co user, hoac user chi dang nhap bang Google -> cung MOT cau tra loi
        if(credential == null){
            passwordEncoder.matches(request.password(), DUMMY_HASH);   // ton ~100ms nhu that
            throw new InvalidCredentialsException();
        }

        if(credential.getLockedUntil() != null && credential.getLockedUntil().isAfter(Instant.now())){
            throw new AccountLockedException(credential.getLockedUntil());
        }

        if(!passwordEncoder.matches(request.password(), credential.getPasswordHash())){
            short attempts = (short) (credential.getFailedAttempts() + 1);
            if(attempts >= MAX_FAILED_ATTEMPTS){
                credential.setLockedUntil(Instant.now().plus(LOCK_DURATION));
                attempts = 0;                   // mo khoa xong thi duoc thu lai 5 lan
            }
            credential.setFailedAttempts(attempts);
            throw new InvalidCredentialsException();   // noRollbackFor -> van COMMIT so lan sai
        }

        // Kiem tra SAU mat khau: khong co mat khau thi khong duoc biet trang thai tai khoan
        if(user.getStatus() != UserStatus.ACTIVE){
            throw new AccountDisabledException();
        }

        credential.setFailedAttempts((short) 0);
        credential.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        // KHONG goi save(): dirty checking tu sinh UPDATE luc commit

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user){
        return new AuthResponse(
                jwtService.generateAccessToken(user),
                "Bearer",
                jwtService.getAccessTokenTtlSeconds(),
                user.getId(),
                user.getEmail(),
                user.getFullName());
    }
}