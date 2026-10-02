-- Initial SmartTask schema. Existing databases are baselined at version 1.
CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    system_role VARCHAR(16) NOT NULL DEFAULT 'USER',
    account_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    locked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    INDEX idx_users_account_status (account_status),
    CONSTRAINT ck_users_full_name CHECK (CHAR_LENGTH(TRIM(full_name)) > 0),
    CONSTRAINT ck_users_system_role CHECK (system_role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_account_status CHECK (account_status IN ('ACTIVE', 'LOCKED'))
);

CREATE TABLE IF NOT EXISTS projects (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_by_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT NULL,
    start_date DATE NULL,
    due_date DATE NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    archived_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_projects PRIMARY KEY (id),
    INDEX idx_projects_created_by_status (created_by_id, status),
    CONSTRAINT ck_projects_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_projects_dates CHECK (
        start_date IS NULL OR due_date IS NULL OR due_date >= start_date
    ),
    CONSTRAINT ck_projects_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT fk_projects_created_by FOREIGN KEY (created_by_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS project_members (
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    project_role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    joined_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    owner_project_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN project_role = 'OWNER' THEN project_id ELSE NULL END
    ),
    CONSTRAINT pk_project_members PRIMARY KEY (project_id, user_id),
    CONSTRAINT uq_project_members_one_owner UNIQUE (owner_project_id),
    INDEX idx_project_members_user (user_id, project_id),
    CONSTRAINT ck_project_members_role CHECK (project_role IN ('OWNER', 'MEMBER')),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id)
        REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS task_statuses (
    id SMALLINT NOT NULL,
    code VARCHAR(32) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    display_order SMALLINT NOT NULL,
    is_terminal BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_task_statuses PRIMARY KEY (id),
    CONSTRAINT uq_task_statuses_code UNIQUE (code),
    CONSTRAINT uq_task_statuses_display_order UNIQUE (display_order),
    CONSTRAINT ck_task_statuses_code CHECK (CHAR_LENGTH(TRIM(code)) > 0),
    CONSTRAINT ck_task_statuses_display_order CHECK (display_order > 0)
);

INSERT INTO task_statuses (id, code, display_name, display_order, is_terminal, is_active)
SELECT 1, 'TODO', 'Cần làm', 1, FALSE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM task_statuses WHERE code = 'TODO');

INSERT INTO task_statuses (id, code, display_name, display_order, is_terminal, is_active)
SELECT 2, 'IN_PROGRESS', 'Đang thực hiện', 2, FALSE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM task_statuses WHERE code = 'IN_PROGRESS');

INSERT INTO task_statuses (id, code, display_name, display_order, is_terminal, is_active)
SELECT 3, 'DONE', 'Hoàn thành', 3, TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM task_statuses WHERE code = 'DONE');

CREATE TABLE IF NOT EXISTS tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    parent_task_id BIGINT NULL,
    created_by_id BIGINT NOT NULL,
    assignee_id BIGINT NULL,
    status_id SMALLINT NOT NULL DEFAULT 1,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    start_at DATETIME(6) NULL,
    due_at DATETIME(6) NOT NULL,
    estimated_minutes INT NULL,
    board_order BIGINT NOT NULL DEFAULT 0,
    completed_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT uq_tasks_project_id_id UNIQUE (project_id, id),
    INDEX idx_tasks_project_status_board (
        project_id, status_id, deleted_at, board_order
    ),
    INDEX idx_tasks_project_calendar (project_id, deleted_at, due_at),
    INDEX idx_tasks_assignee_work (
        assignee_id, status_id, deleted_at, due_at
    ),
    INDEX idx_tasks_parent (parent_task_id),
    CONSTRAINT ck_tasks_title CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_tasks_dates CHECK (start_at IS NULL OR due_at >= start_at),
    CONSTRAINT ck_tasks_estimated_minutes CHECK (
        estimated_minutes IS NULL OR estimated_minutes > 0
    ),
    CONSTRAINT ck_tasks_board_order CHECK (board_order >= 0),
    CONSTRAINT ck_tasks_not_own_parent CHECK (
        parent_task_id IS NULL OR parent_task_id <> id
    ),
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id)
        REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_parent_same_project FOREIGN KEY (project_id, parent_task_id)
        REFERENCES tasks (project_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_created_by FOREIGN KEY (created_by_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_assignee_member FOREIGN KEY (project_id, assignee_id)
        REFERENCES project_members (project_id, user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_status FOREIGN KEY (status_id)
        REFERENCES task_statuses (id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    task_id BIGINT NULL,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(500) NOT NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    INDEX idx_notifications_recipient_read_created (
        recipient_id, read_at, created_at
    ),
    CONSTRAINT ck_notifications_type CHECK (
        type IN ('PROJECT_MEMBER_ADDED', 'TASK_ASSIGNED')
    ),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notifications_project FOREIGN KEY (project_id)
        REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notifications_task_same_project FOREIGN KEY (project_id, task_id)
        REFERENCES tasks (project_id, id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS ai_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requested_by_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    source_task_id BIGINT NULL,
    request_type VARCHAR(40) NOT NULL,
    request_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    input_data JSON NOT NULL,
    raw_response JSON NULL,
    model_name VARCHAR(100) NULL,
    input_tokens INT NULL,
    output_tokens INT NULL,
    error_message TEXT NULL,
    finished_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ai_requests PRIMARY KEY (id),
    INDEX idx_ai_requests_user_created (requested_by_id, created_at),
    INDEX idx_ai_requests_project_type_created (
        project_id, request_type, created_at
    ),
    CONSTRAINT ck_ai_requests_type CHECK (
        request_type IN (
            'TASK_DECOMPOSITION',
            'PRIORITY_SUGGESTION',
            'SCHEDULE_PLANNING',
            'PROGRESS_ANALYSIS'
        )
    ),
    CONSTRAINT ck_ai_requests_status CHECK (
        request_status IN ('PENDING', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT ck_ai_requests_input_tokens CHECK (
        input_tokens IS NULL OR input_tokens >= 0
    ),
    CONSTRAINT ck_ai_requests_output_tokens CHECK (
        output_tokens IS NULL OR output_tokens >= 0
    ),
    CONSTRAINT fk_ai_requests_requested_by FOREIGN KEY (requested_by_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_requests_project FOREIGN KEY (project_id)
        REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_requests_source_same_project FOREIGN KEY (project_id, source_task_id)
        REFERENCES tasks (project_id, id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS ai_suggestions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    target_task_id BIGINT NULL,
    created_task_id BIGINT NULL,
    suggestion_type VARCHAR(32) NOT NULL,
    proposed_data JSON NOT NULL,
    decision_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    decided_by_id BIGINT NULL,
    decided_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ai_suggestions PRIMARY KEY (id),
    CONSTRAINT uq_ai_suggestions_created_task UNIQUE (created_task_id),
    INDEX idx_ai_suggestions_request_decision (request_id, decision_status),
    CONSTRAINT ck_ai_suggestions_type CHECK (
        suggestion_type IN ('SUBTASK', 'PRIORITY', 'SCHEDULE', 'PROGRESS_INSIGHT')
    ),
    CONSTRAINT ck_ai_suggestions_decision CHECK (
        (decision_status = 'PENDING' AND decided_by_id IS NULL AND decided_at IS NULL)
        OR
        (decision_status IN ('ACCEPTED', 'REJECTED')
            AND decided_by_id IS NOT NULL AND decided_at IS NOT NULL)
    ),
    CONSTRAINT ck_ai_suggestions_created_task_state CHECK (
        created_task_id IS NULL OR decision_status = 'ACCEPTED'
    ),
    CONSTRAINT fk_ai_suggestions_request FOREIGN KEY (request_id)
        REFERENCES ai_requests (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_suggestions_target_task FOREIGN KEY (target_task_id)
        REFERENCES tasks (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_suggestions_created_task FOREIGN KEY (created_task_id)
        REFERENCES tasks (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_suggestions_decided_by FOREIGN KEY (decided_by_id)
        REFERENCES users (id) ON DELETE RESTRICT
);
