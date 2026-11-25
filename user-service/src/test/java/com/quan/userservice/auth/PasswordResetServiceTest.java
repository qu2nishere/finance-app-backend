package com.quan.userservice.auth;

import com.quan.userservice.auth.exception.InvalidOtpException;
import com.quan.userservice.user.User;
import com.quan.userservice.user.UserRepository;
import com.quan.userservice.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private OtpMailSender otpMailSender;

    @Mock
    private RefreshTokenService refreshTokenService;

    private PasswordEncoder passwordEncoder;
    private OtpService otpService;
    private PasswordResetService passwordResetService;

    private User user;
    private Credential credential;

    @BeforeEach
    void setUp() {
        // Strength 4 cho unit test chay nhanh ma van kiem tra BCrypt that
        passwordEncoder = new BCryptPasswordEncoder(4);
        otpService = new OtpService(verificationTokenRepository, passwordEncoder, Duration.ofMinutes(10));
        passwordResetService = new PasswordResetService(
                userRepository,
                credentialRepository,
                verificationTokenRepository,
                otpService,
                otpMailSender,
                refreshTokenService,
                passwordEncoder,
                3);

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("quan@gmail.com");
        user.setFullName("Nguyễn Minh");
        user.setStatus(UserStatus.ACTIVE);

        credential = new Credential();
        credential.setUser(user);
        credential.setPasswordHash(passwordEncoder.encode("old-password-123"));
        credential.setFailedAttempts((short) 3);
        credential.setLockedUntil(Instant.now().plus(Duration.ofMinutes(10)));
    }

    @Test
    void requestReset_withUnknownEmail_doesNotIssueOtpOrSendMail() {
        when(userRepository.findByEmailAndDeletedAtIsNull("unknown@gmail.com"))
                .thenReturn(Optional.empty());

        passwordResetService.requestReset("  UNKNOWN@gmail.com ");

        verifyNoInteractions(verificationTokenRepository, otpMailSender);
    }

    @Test
    void requestReset_withGoogleOnlyAccount_doesNotIssueOtpOrSendMail() {
        when(userRepository.findByEmailAndDeletedAtIsNull("quan@gmail.com"))
                .thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(user.getId()))
                .thenReturn(Optional.empty());

        passwordResetService.requestReset("quan@gmail.com");

        verifyNoInteractions(verificationTokenRepository, otpMailSender);
    }

    @Test
    void requestReset_whenFourthCodeInHour_doesNotIssueOtpOrSendMail() {
        when(userRepository.findByEmailAndDeletedAtIsNull("quan@gmail.com"))
                .thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(user.getId()))
                .thenReturn(Optional.of(credential));
        when(verificationTokenRepository.countByUserIdAndPurposeAndCreatedAtAfter(
                eq(user.getId()),
                eq(VerificationToken.Purpose.RESET_PASSWORD),
                any(Instant.class)))
                .thenReturn(3L);

        passwordResetService.requestReset("quan@gmail.com");

        verify(verificationTokenRepository, never()).save(any());
        verifyNoInteractions(otpMailSender);
    }

    @Test
    void requestReset_withEligibleAccount_savesBcryptTokenAndSendsSixDigitCode() {
        when(userRepository.findByEmailAndDeletedAtIsNull("quan@gmail.com"))
                .thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(user.getId()))
                .thenReturn(Optional.of(credential));
        when(verificationTokenRepository.countByUserIdAndPurposeAndCreatedAtAfter(
                eq(user.getId()),
                eq(VerificationToken.Purpose.RESET_PASSWORD),
                any(Instant.class)))
                .thenReturn(2L);

        passwordResetService.requestReset("Quan@Gmail.com");

        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(verificationTokenRepository).save(tokenCaptor.capture());
        verify(otpMailSender).sendPasswordResetCode(
                eq("quan@gmail.com"),
                codeCaptor.capture(),
                eq(Duration.ofMinutes(10)));

        String sentCode = codeCaptor.getValue();
        VerificationToken savedToken = tokenCaptor.getValue();
        assertThat(sentCode).matches("\\d{6}");
        assertThat(savedToken.getPurpose()).isEqualTo(VerificationToken.Purpose.RESET_PASSWORD);
        assertThat(savedToken.getCodeHash()).isNotEqualTo(sentCode);
        assertThat(passwordEncoder.matches(sentCode, savedToken.getCodeHash())).isTrue();
        assertThat(savedToken.getMaxAttempts()).isEqualTo((short) 5);
        assertThat(savedToken.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofMinutes(9)));
    }

    @Test
    void resetPassword_withValidCode_updatesHashClearsLockConsumesTokensAndRevokesAllSessions() {
        VerificationToken activeToken = buildToken("042819", Instant.now().plus(Duration.ofMinutes(10)));
        VerificationToken olderUnconsumed = buildToken("111222", Instant.now().plus(Duration.ofMinutes(5)));
        stubUserCredentialAndToken(activeToken);
        when(verificationTokenRepository.findByUserIdAndPurposeAndConsumedAtIsNull(
                user.getId(), VerificationToken.Purpose.RESET_PASSWORD))
                .thenReturn(List.of(activeToken, olderUnconsumed));

        passwordResetService.resetPassword("quan@gmail.com", "042819", "new-password-456");

        assertThat(passwordEncoder.matches("new-password-456", credential.getPasswordHash())).isTrue();
        assertThat(credential.getFailedAttempts()).isZero();
        assertThat(credential.getLockedUntil()).isNull();
        assertThat(activeToken.getConsumedAt()).isNotNull();
        assertThat(olderUnconsumed.getConsumedAt()).isNotNull();
        verify(refreshTokenService).revokeAllForUser(user.getId(), "PASSWORD_RESET");
    }

    @Test
    void resetPassword_withWrongCode_incrementsAttempts() {
        VerificationToken token = buildToken("123456", Instant.now().plus(Duration.ofMinutes(10)));
        stubUserCredentialAndToken(token);

        assertThatThrownBy(() -> passwordResetService.resetPassword("quan@gmail.com", "999999", "new-password-456"))
                .isInstanceOf(InvalidOtpException.class);

        assertThat(token.getAttempts()).isEqualTo((short) 1);
        assertThat(token.getConsumedAt()).isNull();
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void resetPassword_afterFiveWrongAttempts_rejectsEvenCorrectCode() {
        VerificationToken token = buildToken("123456", Instant.now().plus(Duration.ofMinutes(10)));
        stubUserCredentialAndToken(token);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> passwordResetService.resetPassword("quan@gmail.com", "000000", "new-password-456"))
                    .isInstanceOf(InvalidOtpException.class);
        }
        assertThat(token.getAttempts()).isEqualTo((short) 5);

        assertThatThrownBy(() -> passwordResetService.resetPassword("quan@gmail.com", "123456", "new-password-456"))
                .isInstanceOf(InvalidOtpException.class);
        assertThat(token.getConsumedAt()).isNull();
    }

    @Test
    void resetPassword_withExpiredOrConsumedToken_rejectsCode() {
        VerificationToken expired = buildToken("123456", Instant.now().minusSeconds(1));
        stubUserCredentialAndToken(expired);

        assertThatThrownBy(() -> passwordResetService.resetPassword("quan@gmail.com", "123456", "new-password-456"))
                .isInstanceOf(InvalidOtpException.class);

        VerificationToken consumed = buildToken("123456", Instant.now().plus(Duration.ofMinutes(5)));
        consumed.setConsumedAt(Instant.now().minusSeconds(10));
        when(verificationTokenRepository.findLatestUnconsumedForUpdate(
                user.getId(), VerificationToken.Purpose.RESET_PASSWORD))
                .thenReturn(Optional.of(consumed));

        assertThatThrownBy(() -> passwordResetService.resetPassword("quan@gmail.com", "123456", "new-password-456"))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void verifyCode_withValidCode_doesNotConsumeTokenSoResetPasswordStillWorks() {
        VerificationToken token = buildToken("654321", Instant.now().plus(Duration.ofMinutes(10)));
        stubUserCredentialAndToken(token);
        when(verificationTokenRepository.findByUserIdAndPurposeAndConsumedAtIsNull(
                user.getId(), VerificationToken.Purpose.RESET_PASSWORD))
                .thenReturn(List.of(token));

        passwordResetService.verifyCode("quan@gmail.com", "654321");

        assertThat(token.getConsumedAt()).isNull();
        assertThat(token.getAttempts()).isZero();

        passwordResetService.resetPassword("quan@gmail.com", "654321", "new-password-789");

        assertThat(token.getConsumedAt()).isNotNull();
        assertThat(passwordEncoder.matches("new-password-789", credential.getPasswordHash())).isTrue();
    }

    @Test
    void verifyCode_withWrongCode_incrementsAttempts() {
        VerificationToken token = buildToken("654321", Instant.now().plus(Duration.ofMinutes(10)));
        stubUserCredentialAndToken(token);

        assertThatThrownBy(() -> passwordResetService.verifyCode("quan@gmail.com", "111111"))
                .isInstanceOf(InvalidOtpException.class);

        assertThat(token.getAttempts()).isEqualTo((short) 1);
        assertThat(token.getConsumedAt()).isNull();
    }

    @Test
    void verifyAndReset_threeWrongOnVerifyPlusTwoWrongOnReset_locksToken() {
        VerificationToken token = buildToken("654321", Instant.now().plus(Duration.ofMinutes(10)));
        stubUserCredentialAndToken(token);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> passwordResetService.verifyCode("quan@gmail.com", "000000"))
                    .isInstanceOf(InvalidOtpException.class);
        }
        assertThat(token.getAttempts()).isEqualTo((short) 3);

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> passwordResetService.resetPassword("quan@gmail.com", "000000", "new-password-789"))
                    .isInstanceOf(InvalidOtpException.class);
        }
        assertThat(token.getAttempts()).isEqualTo((short) 5);

        // Lan thu 6 bang ma DUNG van bi tu choi o ca /verify lan /reset-password
        assertThatThrownBy(() -> passwordResetService.verifyCode("quan@gmail.com", "654321"))
                .isInstanceOf(InvalidOtpException.class);
        assertThatThrownBy(() -> passwordResetService.resetPassword("quan@gmail.com", "654321", "new-password-789"))
                .isInstanceOf(InvalidOtpException.class);
    }

    private void stubUserCredentialAndToken(VerificationToken token) {
        when(userRepository.findByEmailAndDeletedAtIsNull("quan@gmail.com"))
                .thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(user.getId()))
                .thenReturn(Optional.of(credential));
        when(verificationTokenRepository.findLatestUnconsumedForUpdate(
                user.getId(), VerificationToken.Purpose.RESET_PASSWORD))
                .thenReturn(Optional.of(token));
    }

    private VerificationToken buildToken(String rawCode, Instant expiresAt) {
        VerificationToken token = new VerificationToken();
        token.setId(UUID.randomUUID());
        token.setUser(user);
        token.setPurpose(VerificationToken.Purpose.RESET_PASSWORD);
        token.setDestination(user.getEmail());
        token.setCodeHash(passwordEncoder.encode(rawCode));
        token.setAttempts((short) 0);
        token.setMaxAttempts((short) 5);
        token.setExpiresAt(expiresAt);
        return token;
    }
}
