-- H2 equivalent of the MySQL notification-event migration.
ALTER TABLE notifications
    DROP CONSTRAINT ck_notifications_type;

ALTER TABLE notifications
    ADD COLUMN dedupe_key VARCHAR(96) NULL;

ALTER TABLE notifications
    ADD CONSTRAINT uq_notifications_recipient_dedupe UNIQUE (recipient_id, dedupe_key);

ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type CHECK (
        type IN (
            'PROJECT_MEMBER_ADDED',
            'TASK_ASSIGNED',
            'TASK_DUE_SOON',
            'TASK_OVERDUE',
            'TASK_STATUS_CHANGED'
        )
    );
