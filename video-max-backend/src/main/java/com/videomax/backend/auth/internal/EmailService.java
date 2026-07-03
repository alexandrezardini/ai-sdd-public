package com.videomax.backend.auth.internal;

import com.videomax.backend.config.MailProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    public EmailService(JavaMailSender mailSender, MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    @Async
    public void sendPasswordResetEmail(String email, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.fromAddress());
        message.setTo(email);
        message.setSubject("Reset Your Video Max Password");
        message.setText(buildResetEmailBody(resetLink));

        try {
            mailSender.send(message);
        } catch (Exception e) {
            // Log the error but don't throw - this is best effort
            System.err.println("Failed to send password reset email to " + email + ": " + e.getMessage());
        }
    }

    private String buildResetEmailBody(String resetLink) {
        return "You requested to reset your password for Video Max.\n\n" +
               "Click the link below to set a new password:\n" +
               resetLink + "\n\n" +
               "This link expires in 30 minutes.\n\n" +
               "If you didn't request this, please ignore this email.\n\n" +
               "Best regards,\n" +
               "Video Max Team";
    }
}
