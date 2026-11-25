package com.quan.userservice.auth;

import com.quan.userservice.auth.exception.InvalidOtpException;
import com.quan.userservice.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000;
    private static final short DEFAULT_MAX_ATTEMPTS = 5;

    // Chong timing side-channel khi khong tim thay user/credential/token tren nhanh kiem ma
    private static final String DUMMY_OTP_HASH = new BCryptPasswordEncoder().encode("000000");

    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Duration otpTtl;

    public OtpService(
            VerificationTokenRepository verificationTokenRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.otp.ttl:10m}") Duration otpTtl) {
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpTtl = otpTtl;
    }

    public Duration getOtpTtl() {
        return otpTtl;
    }

    /** Sinh ma 6 chu so (giu so 0 dau) va luu BCrypt hash vao DB trong transaction rieng. */
    @Transactional
    public String issue(User user, VerificationToken.Purpose purpose) {
        String rawCode = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(CODE_BOUND));

        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setPurpose(purpose);
        token.setDestination(user.getEmail());
        token.setCodeHash(passwordEncoder.encode(rawCode));
        token.setAttempts((short) 0);
        token.setMaxAttempts(DEFAULT_MAX_ATTEMPTS);
        token.setExpiresAt(Instant.now().plus(otpTtl));
        verificationTokenRepository.save(token);

        return rawCode;
    }

    /**
     * Kiem ma nhung KHONG tieu ma (phuc vu buoc 2 /reset-password/verify - D12-A).
     * noRollbackFor: khi ma sai van phai COMMIT attempts++ de gioi han toi da 5 lan thu.
     */
    @Transactional(noRollbackFor = InvalidOtpException.class)
    public VerificationToken check(User user, VerificationToken.Purpose purpose, String rawCode) {
        VerificationToken token = verificationTokenRepository
                .findLatestUnconsumedForUpdate(user.getId(), purpose)
                .orElse(null);

        Instant now = Instant.now();
        if (token == null
                || token.getConsumedAt() != null
                || !token.getExpiresAt().isAfter(now)
                || token.getAttempts() >= token.getMaxAttempts()) {
            runDummyCheck(rawCode);
            throw new InvalidOtpException();
        }

        if (!passwordEncoder.matches(rawCode, token.getCodeHash())) {
            token.setAttempts((short) (token.getAttempts() + 1));
            throw new InvalidOtpException();
        }

        return token;
    }

    /** Kiem ma va tieu ngay ma do cung cac ma con song cung muc dich cua user. */
    @Transactional(noRollbackFor = InvalidOtpException.class)
    public VerificationToken verify(User user, VerificationToken.Purpose purpose, String rawCode) {
        VerificationToken token = check(user, purpose, rawCode);
        consumeAll(user.getId(), purpose, token);
        return token;
    }

    @Transactional
    public void consumeAll(UUID userId, VerificationToken.Purpose purpose, VerificationToken currentToken) {
        Instant now = Instant.now();
        currentToken.setConsumedAt(now);
        List<VerificationToken> remaining = verificationTokenRepository
                .findByUserIdAndPurposeAndConsumedAtIsNull(userId, purpose);
        for (VerificationToken other : remaining) {
            other.setConsumedAt(now);
        }
    }

    void runDummyCheck(String rawCode) {
        passwordEncoder.matches(rawCode, DUMMY_OTP_HASH);
    }
}
