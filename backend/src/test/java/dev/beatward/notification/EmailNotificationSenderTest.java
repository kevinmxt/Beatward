package dev.beatward.notification;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailNotificationSenderTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @Test
    void sendsThroughTheGlobalSmtpSender() {
        EmailNotificationSender sender = new EmailNotificationSender(mailSender, configuredSmtp());

        sender.send(Map.of("email", "owner@example.com"), "标题", "内容");

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("beatward@example.com");
        assertThat(message.getValue().getTo()).containsExactly("owner@example.com");
        assertThat(message.getValue().getSubject()).isEqualTo("标题");
        assertThat(message.getValue().getText()).isEqualTo("内容");
    }

    @Test
    void reportsMissingSmtpWithoutRetrying() {
        EmailNotificationSender sender = new EmailNotificationSender(mailSender,
                new SmtpProperties(null, null, null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> sender.send(Map.of("email", "owner@example.com"), "t", "c"))
                .isInstanceOfSatisfying(NotificationSendException.class, failure -> {
                    assertThat(failure.getMessage()).isEqualTo("SMTP is not configured");
                    assertThat(failure.isRetryable()).isFalse();
                });
    }

    @Test
    void mapsMailFailuresToRetryableErrorsWithoutAddresses() {
        doThrow(new MailSendException("cannot reach smtp")).when(mailSender)
                .send(any(SimpleMailMessage.class));
        EmailNotificationSender sender = new EmailNotificationSender(mailSender, configuredSmtp());

        assertThatThrownBy(() -> sender.send(Map.of("email", "owner@example.com"), "t", "c"))
                .isInstanceOfSatisfying(NotificationSendException.class, failure -> {
                    assertThat(failure.isRetryable()).isTrue();
                    assertThat(failure.getMessage())
                            .contains("MailSendException")
                            .doesNotContain("owner@example.com");
                });
    }

    private static SmtpProperties configuredSmtp() {
        return new SmtpProperties("smtp.example.com", 465, "user", "secret",
                "beatward@example.com", false, true, 5000, 10000, 10000);
    }
}
