package com.quan.userservice.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class OtpMailSender {

    private final JavaMailSender mailSender;
    private final String mailUser;
    private final boolean devLog;

    public OtpMailSender(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String mailUser,
            @Value("${app.otp.dev-log:false}") boolean devLog) {
        this.mailSender = mailSender;
        this.mailUser = mailUser;
        this.devLog = devLog;
    }

    public void sendPasswordResetCode(String recipientEmail, String rawCode, Duration ttl) {
        long minutes = Math.max(1, ttl.toMinutes());
        boolean hasSmtp = mailUser != null && !mailUser.isBlank();

        if (devLog) {
            // Chi bat tuong minh o may dev (OTP_DEV_LOG=true); mac dinh tat de ma OTP khong bao gio lot ra log that
            log.warn("[DEV ONLY] Password reset OTP for {}: {} (expires in {} minutes)",
                    recipientEmail, rawCode, minutes);
        }

        if (hasSmtp) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailUser);
            message.setTo(recipientEmail);
            message.setSubject("Mã đặt lại mật khẩu — Money Flow");
            message.setText(
                    "Xin chào,\n\n"
                            + "Mã xác nhận đặt lại mật khẩu Money Flow của bạn là: " + rawCode + "\n"
                            + "Mã có hiệu lực trong " + minutes + " phút. Không chia sẻ mã này cho bất kỳ ai.\n\n"
                            + "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.");
            try {
                mailSender.send(message);
            } catch (Exception ex) {
                log.error("Failed to send password reset email via SMTP", ex);
            }
            return;
        }

        if (!devLog) {
            log.error("Cannot deliver password reset OTP: MAIL_USER is empty and app.otp.dev-log=false");
        }
    }
}
