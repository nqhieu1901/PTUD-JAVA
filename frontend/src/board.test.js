import { describe, expect, it } from "vitest";
import { appendPosition, isAdjacentStatusTransition, moveTaskLocally, tasksInStatus } from "./board.js";

const task = (id, status, boardOrder) => ({ id, boardOrder, status: { code: status } });

describe("Kanban board ordering", () => {
  it("reorders a task and keeps a dense zero-based order", () => {
    const tasks = [task(1, "TODO", 0), task(2, "TODO", 1), task(3, "TODO", 2)];
    const moved = moveTaskLocally(tasks, 1, "TODO", 2);
    expect(tasksInStatus(moved, "TODO").map(({ id, boardOrder }) => [id, boardOrder])).toEqual([
      [2, 0], [3, 1], [1, 2]
    ]);
  });

  it("moves between columns and compacts both columns", () => {
    const tasks = [
      task(1, "TODO", 0), task(2, "TODO", 1), task(3, "TODO", 2),
      task(4, "IN_PROGRESS", 0), task(5, "IN_PROGRESS", 1)
    ];
    const moved = moveTaskLocally(tasks, 2, "IN_PROGRESS", 1);
    expect(tasksInStatus(moved, "TODO").map((item) => [item.id, item.boardOrder])).toEqual([[1, 0], [3, 1]]);
    expect(tasksInStatus(moved, "IN_PROGRESS").map((item) => [item.id, item.boardOrder])).toEqual([
      [4, 0], [2, 1], [5, 2]
    ]);
    expect(appendPosition(moved, "DONE")).toBe(0);
  });

  it("clamps an oversized target position to the end", () => {
    const moved = moveTaskLocally([task(1, "TODO", 0), task(2, "DONE", 0)], 1, "DONE", 99);
    expect(tasksInStatus(moved, "DONE").map((item) => [item.id, item.boardOrder])).toEqual([[2, 0], [1, 1]]);
  });

  it("moves to a custom column and applies its workflow metadata", () => {
    const statuses = [
      { id: 1, code: "TODO", displayName: "Chờ", terminal: false },
      { id: 9, code: "CUSTOM_REVIEW", displayName: "Kiểm thử", terminal: true }
    ];
    const moved = moveTaskLocally([task(1, "TODO", 0)], 1, "CUSTOM_REVIEW", 0, statuses);

    expect(moved[0].status).toMatchObject({ id: 9, code: "CUSTOM_REVIEW", displayName: "Kiểm thử", terminal: true });
  });

  it("only allows transitions within the same or an adjacent workflow column", () => {
    const statuses = [
      { code: "TODO", displayOrder: 1 },
      { code: "IN_PROGRESS", displayOrder: 2 },
      { code: "REVIEW", displayOrder: 3 },
      { code: "DONE", displayOrder: 4 }
    ];

    expect(isAdjacentStatusTransition(statuses, "TODO", "TODO")).toBe(true);
    expect(isAdjacentStatusTransition(statuses, "TODO", "IN_PROGRESS")).toBe(true);
    expect(isAdjacentStatusTransition(statuses, "REVIEW", "IN_PROGRESS")).toBe(true);
    expect(isAdjacentStatusTransition(statuses, "TODO", "DONE")).toBe(false);
  });
});
