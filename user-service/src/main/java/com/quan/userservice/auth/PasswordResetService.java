package com.quan.userservice.auth;

import com.quan.userservice.auth.exception.InvalidOtpException;
import com.quan.userservice.user.User;
import com.quan.userservice.user.UserRepository;
import com.quan.userservice.user.UserStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Slf4j
@Service
public class PasswordResetService {

    private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final OtpService otpService;
    private final OtpMailSender otpMailSender;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final int maxPerHour;

    record CheckedReset(User user, Credential credential, VerificationToken token) {
    }

    public PasswordResetService(
            UserRepository userRepository,
            CredentialRepository credentialRepository,
            VerificationTokenRepository verificationTokenRepository,
            OtpService otpService,
            OtpMailSender otpMailSender,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder,
            @Value("${app.otp.max-per-hour:3}") int maxPerHour) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.otpService = otpService;
        this.otpMailSender = otpMailSender;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.maxPerHour = maxPerHour;
    }

    /**
     * Chay bat dong bo (D9) de POST /forgot-password tra 204 ngay lap tuc, khong lo email ton tai
     * qua do tre BCrypt + SMTP. Khong dat @Transactional o day de SMTP chay SAU khi OtpService.issue commit (N4).
     */
    @Async("otpExecutor")
    public void requestReset(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        User user = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return;
        }
        // D10: tai khoan chi dang nhap Google (khong co credentials) -> im lang dung, van 204
        if (credentialRepository.findByUserId(user.getId()).isEmpty()) {
            return;
        }

        long issuedInLastHour = verificationTokenRepository.countByUserIdAndPurposeAndCreatedAtAfter(
                user.getId(),
                VerificationToken.Purpose.RESET_PASSWORD,
                Instant.now().minus(RATE_LIMIT_WINDOW));
        if (issuedInLastHour >= maxPerHour) {
            log.warn("Password reset OTP rate limit reached for user {}", user.getId());
            return;
        }

        String rawCode = otpService.issue(user, VerificationToken.Purpose.RESET_PASSWORD);
        otpMailSender.sendPasswordResetCode(user.getEmail(), rawCode, otpService.getOtpTtl());
    }

    // noRollbackFor -> giu nguyen attempts++ trong DB khi nguoi dung nhap sai ma o man 2
    @Transactional(noRollbackFor = InvalidOtpException.class)
    public void verifyCode(String rawEmail, String code) {
        checkCode(rawEmail, code);
    }

    // noRollbackFor -> giu nguyen attempts++ trong DB khi nhap sai ma o buoc cuoi
    @Transactional(noRollbackFor = InvalidOtpException.class)
    public void resetPassword(String rawEmail, String code, String newPassword) {
        CheckedReset checked = checkCode(rawEmail, code);

        otpService.consumeAll(
                checked.user().getId(),
                VerificationToken.Purpose.RESET_PASSWORD,
                checked.token());

        Credential credential = checked.credential();
        credential.setPasswordHash(passwordEncoder.encode(newPassword));
        credential.setPasswordChangedAt(Instant.now());
        credential.setFailedAttempts((short) 0);
        credential.setLockedUntil(null);

        refreshTokenService.revokeAllForUser(checked.user().getId(), "PASSWORD_RESET");
    }

    CheckedReset checkCode(String rawEmail, String code) {
        String email = normalizeEmail(rawEmail);
        User user = userRepository.findByEmailAndDeletedAtIsNull(email).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            otpService.runDummyCheck(code);
            throw new InvalidOtpException();
        }

        Credential credential = credentialRepository.findByUserId(user.getId()).orElse(null);
        if (credential == null) {
            otpService.runDummyCheck(code);
            throw new InvalidOtpException();
        }

        VerificationToken token = otpService.check(user, VerificationToken.Purpose.RESET_PASSWORD, code);
        return new CheckedReset(user, credential, token);
    }

    private static String normalizeEmail(String rawEmail) {
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }
}
