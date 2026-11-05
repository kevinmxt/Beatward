package dev.beatward.notification;

import dev.beatward.common.SchedulingSupport;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Properties;

@Configuration
@EnableConfigurationProperties({TelegramProperties.class, NotificationProperties.class,
        NotificationChannelProperties.class, SmtpProperties.class,
        NotificationEndpointProperties.class})
public class NotificationConfiguration {

    @Bean
    ThreadPoolTaskScheduler notificationTickScheduler() {
        return SchedulingSupport.singleThreadScheduler("notification-tick-");
    }

    @Bean(destroyMethod = "close")
    CloseableHttpClient notificationHttpClient() {
        return HttpClientBuilder.create()
                .setConnectionManager(new PoolingHttpClientConnectionManager())
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .build();
    }

    @Bean
    Gauge notificationPendingGauge(NotificationDeliveryRepository deliveryRepository,
                                   MeterRegistry meterRegistry) {
        return Gauge.builder("notification.pending", deliveryRepository,
                        repository -> repository.countByStatus(NotificationDeliveryStatus.PENDING))
                .register(meterRegistry);
    }

    @Bean
    JavaMailSender javaMailSender(SmtpProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.host());
        sender.setPort(properties.port());
        sender.setUsername(properties.username());
        sender.setPassword(properties.password());
        sender.setDefaultEncoding("UTF-8");
        Properties mail = sender.getJavaMailProperties();
        mail.put("mail.smtp.auth", Boolean.toString(properties.username() != null));
        mail.put("mail.smtp.starttls.enable", Boolean.toString(properties.starttls()));
        mail.put("mail.smtp.ssl.enable", Boolean.toString(properties.ssl()));
        mail.put("mail.smtp.connectiontimeout", properties.connectTimeoutMs());
        mail.put("mail.smtp.timeout", properties.readTimeoutMs());
        mail.put("mail.smtp.writetimeout", properties.writeTimeoutMs());
        return sender;
    }
}
