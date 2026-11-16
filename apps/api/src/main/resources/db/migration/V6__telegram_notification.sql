CREATE TABLE telegram_notification (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    dedup_key   VARCHAR(100) NOT NULL,
    post_id     BIGINT       NULL,
    kind        VARCHAR(40)  NOT NULL,
    message_id  BIGINT       NULL,
    sent_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_telegram_notification_key UNIQUE (dedup_key)
);
CREATE INDEX idx_telegram_notification_post_kind ON telegram_notification (post_id, kind);
