ALTER TABLE tasks DROP FOREIGN KEY fk_tasks_status;
ALTER TABLE task_statuses DROP INDEX uq_task_statuses_code;
ALTER TABLE task_statuses DROP INDEX uq_task_statuses_display_order;

ALTER TABLE task_statuses ADD COLUMN project_id BIGINT NULL AFTER id;
ALTER TABLE task_statuses MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE tasks MODIFY COLUMN status_id BIGINT NOT NULL;

INSERT INTO task_statuses (project_id, code, display_name, display_order, is_terminal, is_active)
SELECT project.id, template.code, template.display_name,
       template.display_order, template.is_terminal, template.is_active
FROM projects project
CROSS JOIN task_statuses template
WHERE template.project_id IS NULL;

UPDATE tasks task
JOIN task_statuses legacy_status
  ON legacy_status.id = task.status_id
 AND legacy_status.project_id IS NULL
JOIN task_statuses workflow_status
  ON workflow_status.project_id = task.project_id
 AND workflow_status.code = legacy_status.code
SET task.status_id = workflow_status.id;

ALTER TABLE task_statuses ADD CONSTRAINT fk_task_statuses_project
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE RESTRICT;
ALTER TABLE task_statuses ADD CONSTRAINT uq_task_statuses_project_code
    UNIQUE (project_id, code);
CREATE INDEX idx_task_statuses_project_order
    ON task_statuses (project_id, display_order);
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_status FOREIGN KEY (status_id)
    REFERENCES task_statuses (id) ON DELETE RESTRICT;
