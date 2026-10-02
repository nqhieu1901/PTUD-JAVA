import { describe, expect, it } from "vitest";
import { deadlineTasks, monthGrid, summarizeTasks, tasksByDueDate } from "./planning.js";

const tasks = [
  { id: 1, title: "Trễ", status: { code: "TODO" }, priority: "HIGH", dueAt: "2026-08-20T09:00:00", estimatedMinutes: 30 },
  { id: 2, title: "Sắp tới", status: { code: "IN_PROGRESS" }, priority: "URGENT", dueAt: "2026-08-25T09:00:00", estimatedMinutes: 90 },
  { id: 3, title: "Xong", status: { code: "DONE" }, priority: "LOW", dueAt: "2026-08-19T09:00:00", estimatedMinutes: 15 }
];

describe("planning utilities", () => {
  it("summarizes status, deadlines and workload", () => {
    const result = summarizeTasks(tasks, new Date("2026-08-23T12:00:00"));
    expect(result).toMatchObject({ total: 3, completed: 1, completionRate: 33, overdue: 1, dueSoon: 1, estimatedMinutes: 135 });
    expect(result.byStatus).toEqual({ TODO: 1, IN_PROGRESS: 1, DONE: 1 });
  });

  it("returns incomplete tasks ordered by deadline", () => {
    const result = deadlineTasks(tasks, new Date("2026-08-23T12:00:00"));
    expect(result.map((task) => task.id)).toEqual([1, 2]);
    expect(result[0].isOverdue).toBe(true);
  });

  it("groups tasks by due date and builds a Monday-first month", () => {
    expect(Object.keys(tasksByDueDate(tasks))).toEqual(["2026-08-20", "2026-08-25", "2026-08-19"]);
    const grid = monthGrid(2026, 7);
    expect(grid).toHaveLength(42);
    expect(grid[0].key).toBe("2026-07-27");
    expect(grid.find((day) => day.key === "2026-08-01")?.inMonth).toBe(true);
  });

  it("uses workflow terminal metadata instead of a fixed DONE code", () => {
    const custom = [
      { id: 10, title: "Duyệt xong", status: { code: "APPROVED", terminal: true }, priority: "MEDIUM", dueAt: "2026-08-20T09:00:00" },
      { id: 11, title: "Còn làm", status: { code: "DONE", terminal: false }, priority: "LOW", dueAt: "2026-08-20T09:00:00" }
    ];
    const result = summarizeTasks(custom, new Date("2026-08-23T12:00:00"));

    expect(result.completed).toBe(1);
    expect(result.overdue).toBe(1);
    expect(deadlineTasks(custom, new Date("2026-08-23T12:00:00")).map((task) => task.id)).toEqual([11]);
  });
});
