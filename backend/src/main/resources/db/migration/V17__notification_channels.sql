CREATE TABLE notification_channel (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('PUSHPLUS', 'XIZHI', 'EMAIL')),
    name VARCHAR(100) NOT NULL,
    config_ciphertext TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_test_at TIMESTAMPTZ,
    last_test_status VARCHAR(10),
    last_test_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_notification_channel_name UNIQUE (user_id, type, name),
    CONSTRAINT chk_notification_channel_test_status CHECK (
        last_test_status IN ('SUCCESS', 'FAILED')
    )
);

CREATE INDEX idx_notification_channel_user_enabled
    ON notification_channel (user_id, enabled);

ALTER TABLE notification_delivery
    ADD COLUMN notification_channel_id BIGINT
        REFERENCES notification_channel (id) ON DELETE SET NULL;

ALTER TABLE notification_delivery
    DROP CONSTRAINT uq_notification_delivery_event;

ALTER TABLE notification_delivery
    DROP CONSTRAINT notification_delivery_channel_check;

ALTER TABLE notification_delivery
    ADD CONSTRAINT chk_notification_delivery_channel CHECK (
        channel IN ('TELEGRAM', 'PUSHPLUS', 'XIZHI', 'EMAIL')
    );

CREATE UNIQUE INDEX uq_notification_delivery_channel
    ON notification_delivery (incident_id, event_type, notification_channel_id)
    WHERE notification_channel_id IS NOT NULL;

CREATE UNIQUE INDEX uq_notification_delivery_legacy
    ON notification_delivery (incident_id, event_type, channel)
    WHERE notification_channel_id IS NULL AND channel = 'TELEGRAM';

CREATE INDEX idx_notification_delivery_channel_id
    ON notification_delivery (notification_channel_id);
