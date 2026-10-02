ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN credentials_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE account_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    purpose VARCHAR(24) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    issued_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6) NULL,
    credentials_version BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_account_tokens_user_purpose UNIQUE (user_id, purpose),
    CONSTRAINT uq_account_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_account_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_account_tokens_purpose CHECK (purpose IN ('PASSWORD_RESET', 'EMAIL_VERIFICATION'))
);
