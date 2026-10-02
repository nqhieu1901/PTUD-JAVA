ALTER TABLE users ADD COLUMN google_subject VARCHAR(255) NULL;
CREATE UNIQUE INDEX uq_users_google_subject ON users (google_subject);
