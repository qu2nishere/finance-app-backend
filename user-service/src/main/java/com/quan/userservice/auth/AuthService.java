package com.quan.userservice.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.quan.userservice.auth.dto.AuthResponse;
import com.quan.userservice.auth.dto.GoogleLoginRequest;
import com.quan.userservice.auth.dto.LoginRequest;
import com.quan.userservice.auth.dto.RegisterRequest;
import com.quan.userservice.auth.exception.AccountDisabledException;
import com.quan.userservice.auth.exception.AccountLockedException;
import com.quan.userservice.auth.exception.EmailAlreadyUsedException;
import com.quan.userservice.auth.exception.InvalidCredentialsException;
import com.quan.userservice.common.security.JwtService;
import com.quan.userservice.identity.UserIdentity;
import com.quan.userservice.identity.UserIdentityRepository;
import com.quan.userservice.settings.NotificationSettings;
import com.quan.userservice.settings.NotificationSettingsRepository;
import com.quan.userservice.settings.UserSettings;
import com.quan.userservice.settings.UserSettingsRepository;
import com.quan.userservice.user.User;
import com.quan.userservice.user.UserRepository;
import com.quan.userservice.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserIdentityRepository userIdentityRepository;
    private final GoogleTokenVerifierService googleTokenVerifierService;

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

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdToken.Payload payload = googleTokenVerifierService.verify(request.idToken());

        String googleSub = payload.getSubject();
        String email = payload.getEmail().trim().toLowerCase(Locale.ROOT);
        String fullName = (String) payload.get("name");
        String avatarUrl = (String) payload.get("picture");

        // 1. Kiểm tra đã có Google Identity chưa
        UserIdentity identity = userIdentityRepository
                .findByProviderAndProviderUserId("GOOGLE", googleSub)
                .orElse(null);

        User user;
        if (identity != null) {
            user = identity.getUser();
            identity.setLastLoginAt(Instant.now());
        } else {
            // 2. Chưa có identity -> Kiểm tra User theo email
            user = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);
            if (user == null) {
                // 3. User mới hoàn toàn -> Đăng ký tự động
                user = new User();
                user.setEmail(email);
                user.setFullName(fullName != null && !fullName.isBlank() ? fullName.trim() : "User");
                user.setAvatarUrl(avatarUrl);
                user.setEmailVerified(true);
                user.setEmailVerifiedAt(Instant.now());
                userRepository.save(user);

                userSettingsRepository.save(new UserSettings(user));
                notificationSettingsRepository.save(new NotificationSettings(user));
            } else {
                // User cũ -> Link tài khoản Google
                if (!user.isEmailVerified()) {
                    user.setEmailVerified(true);
                    user.setEmailVerifiedAt(Instant.now());
                }
            }

            // Tạo bản ghi UserIdentity
            UserIdentity newIdentity = new UserIdentity();
            newIdentity.setUser(user);
            newIdentity.setProvider("GOOGLE");
            newIdentity.setProviderUserId(googleSub);
            newIdentity.setEmail(email);
            newIdentity.setDisplayName(fullName);
            newIdentity.setAvatarUrl(avatarUrl);
            newIdentity.setLastLoginAt(Instant.now());
            userIdentityRepository.save(newIdentity);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountDisabledException();
        }

        user.setLastLoginAt(Instant.now());
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