package dev.beatward.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "notification_channel")
public class NotificationChannelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel type;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "config_ciphertext", nullable = false, columnDefinition = "text")
    private String configCiphertext;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "last_test_at")
    private Instant lastTestAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_test_status", length = 10)
    private ChannelTestStatus lastTestStatus;

    @Column(name = "last_test_error", length = 500)
    private String lastTestError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public NotificationChannel getType() {
        return type;
    }

    public void setType(NotificationChannel type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getConfigCiphertext() {
        return configCiphertext;
    }

    public void setConfigCiphertext(String configCiphertext) {
        this.configCiphertext = configCiphertext;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getLastTestAt() {
        return lastTestAt;
    }

    public void setLastTestAt(Instant lastTestAt) {
        this.lastTestAt = lastTestAt;
    }

    public ChannelTestStatus getLastTestStatus() {
        return lastTestStatus;
    }

    public void setLastTestStatus(ChannelTestStatus lastTestStatus) {
        this.lastTestStatus = lastTestStatus;
    }

    public String getLastTestError() {
        return lastTestError;
    }

    public void setLastTestError(String lastTestError) {
        this.lastTestError = lastTestError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
