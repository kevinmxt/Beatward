package dev.beatward.notification;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EmailNotificationSender implements UserChannelSender {

    private final JavaMailSender mailSender;
    private final SmtpProperties smtp;

    public EmailNotificationSender(JavaMailSender mailSender, SmtpProperties smtp) {
        this.mailSender = mailSender;
        this.smtp = smtp;
    }

    @Override
    public NotificationChannel type() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(Map<String, String> credentials, String title, String content) {
        if (!smtp.isConfigured()) {
            throw new NotificationSendException("SMTP is not configured", false);
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(smtp.from());
        message.setTo(credentials.get("email"));
        message.setSubject(title);
        message.setText(content);
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            throw new NotificationSendException(
                    "Email send failed: " + ex.getClass().getSimpleName(), true, ex);
        }
    }
}
