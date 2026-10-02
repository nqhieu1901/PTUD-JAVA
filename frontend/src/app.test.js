import { describe, expect, it } from "vitest";
import {
  canWriteTaskAttachments, canWriteTaskComments, replaceProjectResponse,
  replaceUserInMembers, replaceUserInProjects, replaceUserInTasks, shouldLoadUserWorkspace
} from "./App.jsx";

describe("project response state", () => {
  it("replaces the archived project with the restore response immediately", () => {
    const active = { id: 1, name: "Dự án khác", status: "ACTIVE" };
    const archived = { id: 2, name: "Website", status: "ARCHIVED", archivedAt: "2026-08-24T08:00:00" };
    const restored = { id: 2, name: "Website", status: "ACTIVE", archivedAt: null };

    const result = replaceProjectResponse([active, archived], restored);

    expect(result).toEqual([active, restored]);
    expect(result[0]).toBe(active);
    expect(result[1]).toBe(restored);
  });

  it("updates denormalized user names without changing unrelated records", () => {
    const updatedUser = { id: 7, fullName: "Tên mới" };
    const otherUser = { id: 8, fullName: "Người khác" };
    const projects = [
      { id: 1, createdBy: { id: 7, fullName: "Tên cũ" } },
      { id: 2, createdBy: otherUser }
    ];
    const tasks = [
      { id: 3, createdBy: otherUser, assignee: { id: 7, fullName: "Tên cũ" } },
      { id: 4, createdBy: { id: 7, fullName: "Tên cũ" }, assignee: null }
    ];
    const members = [
      { userId: 7, fullName: "Tên cũ" },
      { userId: 8, fullName: "Người khác" }
    ];

    expect(replaceUserInProjects(projects, updatedUser)[0].createdBy.fullName).toBe("Tên mới");
    expect(replaceUserInProjects(projects, updatedUser)[1]).toBe(projects[1]);
    expect(replaceUserInTasks(tasks, updatedUser).map((task) => [task.createdBy.fullName, task.assignee?.fullName]))
      .toEqual([["Người khác", "Tên mới"], ["Tên mới", undefined]]);
    expect(replaceUserInMembers(members, updatedUser).map((member) => member.fullName))
      .toEqual(["Tên mới", "Người khác"]);
  });
});

describe("comment write policy", () => {
  it("allows owners and members on active projects but keeps viewers and archived projects read-only", () => {
    expect(canWriteTaskComments({ status: "ACTIVE", currentUserRole: "OWNER" })).toBe(true);
    expect(canWriteTaskComments({ status: "ACTIVE", currentUserRole: "MEMBER" })).toBe(true);
    expect(canWriteTaskComments({ status: "ACTIVE", currentUserRole: "VIEWER" })).toBe(false);
    expect(canWriteTaskComments({ status: "ARCHIVED", currentUserRole: "OWNER" })).toBe(false);
    expect(canWriteTaskAttachments({ status: "ACTIVE", currentUserRole: "OWNER" })).toBe(true);
    expect(canWriteTaskAttachments({ status: "ACTIVE", currentUserRole: "MEMBER" })).toBe(true);
    expect(canWriteTaskAttachments({ status: "ACTIVE", currentUserRole: "VIEWER" })).toBe(false);
    expect(canWriteTaskAttachments({ status: "ARCHIVED", currentUserRole: "OWNER" })).toBe(false);
  });
});

describe("system role workspace boundary", () => {
  it("loads project data for users but keeps admins in the administration workspace", () => {
    expect(shouldLoadUserWorkspace({ role: "USER" })).toBe(true);
    expect(shouldLoadUserWorkspace({ role: "ADMIN" })).toBe(false);
  });
});
