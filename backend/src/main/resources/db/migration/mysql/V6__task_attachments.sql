CREATE TABLE task_attachments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    uploaded_by_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(100) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_task_attachments PRIMARY KEY (id),
    CONSTRAINT uq_task_attachments_stored_name UNIQUE (stored_name),
    INDEX idx_task_attachments_task_created (
        project_id, task_id, created_at, id
    ),
    INDEX idx_task_attachments_uploader (uploaded_by_id, created_at),
    CONSTRAINT ck_task_attachments_original_name CHECK (
        CHAR_LENGTH(TRIM(original_name)) > 0
    ),
    CONSTRAINT ck_task_attachments_size CHECK (size_bytes > 0),
    CONSTRAINT fk_task_attachments_project FOREIGN KEY (project_id)
        REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_attachments_task_same_project FOREIGN KEY (project_id, task_id)
        REFERENCES tasks (project_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_attachments_uploaded_by FOREIGN KEY (uploaded_by_id)
        REFERENCES users (id) ON DELETE RESTRICT
);
