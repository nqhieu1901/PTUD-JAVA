ALTER TABLE tasks DROP CONSTRAINT fk_tasks_board;
DROP INDEX idx_tasks_board_status_order;
ALTER TABLE tasks DROP COLUMN board_id;
DROP TABLE boards;
