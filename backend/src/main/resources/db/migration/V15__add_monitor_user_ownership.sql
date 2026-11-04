ALTER TABLE monitor ADD COLUMN user_id BIGINT REFERENCES app_user (id);

CREATE INDEX idx_monitor_user_enabled ON monitor (user_id, enabled);

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
            RAISE EXCEPTION 'V15 requires an active ADMIN to backfill monitor.user_id; '
                'create the bootstrap admin (or upgrade through Phase 4B) first';
        END IF;

        UPDATE monitor SET user_id = admin_id WHERE user_id IS NULL;
    END IF;
END $$;
