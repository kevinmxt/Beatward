DO $$
DECLARE
    admin_id BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM monitor WHERE user_id IS NULL) THEN
        SELECT id
        INTO admin_id
        FROM app_user
        WHERE role = 'ADMIN'
          AND status = 'ACTIVE'
        ORDER BY id
        LIMIT 1;

        IF admin_id IS NULL THEN
            RAISE EXCEPTION 'V16 requires an active ADMIN to backfill monitor.user_id';
        END IF;

        UPDATE monitor SET user_id = admin_id WHERE user_id IS NULL;
    END IF;
END $$;

ALTER TABLE monitor ALTER COLUMN user_id SET NOT NULL;

UPDATE monitor
SET interval_seconds = 60,
    updated_at = NOW()
WHERE type = 'HTTP'
  AND interval_seconds < 60;

ALTER TABLE monitor DROP CONSTRAINT chk_monitor_interval_by_type;
ALTER TABLE monitor ADD CONSTRAINT chk_monitor_interval_by_type CHECK (
    (type = 'HTTP' AND interval_seconds BETWEEN 60 AND 86400)
    OR (type = 'HEARTBEAT' AND interval_seconds BETWEEN 30 AND 2592000)
);
