ALTER TABLE tasks DROP FOREIGN KEY fk_tasks_board;
DROP INDEX idx_tasks_board_status_order ON tasks;
ALTER TABLE tasks DROP COLUMN board_id;
DROP TABLE boards;
