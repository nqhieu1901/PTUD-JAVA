import { describe, expect, it } from "vitest";
import {
  accountStatusLabel,
  canChangeAccountStatus,
  replaceAdminUser
} from "./admin-users.js";

describe("admin user utilities", () => {
  it("replaces only the updated account", () => {
    const users = [{ id: 1, status: "ACTIVE" }, { id: 2, status: "ACTIVE" }];
    const updated = { id: 2, status: "LOCKED" };

    const result = replaceAdminUser(users, updated);

    expect(result).toEqual([users[0], updated]);
    expect(users[1].status).toBe("ACTIVE");
  });

  it("allows an admin to change another account but never the current account", () => {
    const admin = { id: 1, role: "ADMIN" };
    expect(canChangeAccountStatus(admin, { id: 2 })).toBe(true);
    expect(canChangeAccountStatus(admin, { id: 1 })).toBe(false);
    expect(canChangeAccountStatus({ id: 3, role: "USER" }, { id: 2 })).toBe(false);
  });

  it("uses Vietnamese labels for account states", () => {
    expect(accountStatusLabel("ACTIVE")).toBe("Hoạt động");
    expect(accountStatusLabel("LOCKED")).toBe("Đã khóa");
  });
});
