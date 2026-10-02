CREATE TABLE task_comments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(4000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_task_comments PRIMARY KEY (id),
    INDEX idx_task_comments_task_created (
        project_id, task_id, created_at, id
    ),
    INDEX idx_task_comments_author (author_id, created_at),
    CONSTRAINT ck_task_comments_content CHECK (
        CHAR_LENGTH(TRIM(content)) > 0 AND CHAR_LENGTH(content) <= 4000
    ),
    CONSTRAINT fk_task_comments_project FOREIGN KEY (project_id)
        REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_comments_task_same_project FOREIGN KEY (project_id, task_id)
        REFERENCES tasks (project_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_task_comments_author FOREIGN KEY (author_id)
        REFERENCES users (id) ON DELETE RESTRICT
);
