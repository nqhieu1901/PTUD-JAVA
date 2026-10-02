import { describe, expect, it } from "vitest";
import {
  canOpenNotification, countUnread, markAllNotificationsRead, markNotificationRead,
  NOTIFICATION_REFRESH_INTERVAL_MS, shouldAutoRefreshNotifications
} from "./notifications.js";
import { notificationTypeLabel } from "./components/NotificationCenter.jsx";

const notifications = [
  { id: 1, read: false, readAt: null },
  { id: 2, read: true, readAt: "2026-08-23T08:00:00Z" },
  { id: 3, read: false, readAt: null }
];

describe("notification state helpers", () => {
  it("refreshes every minute only while an authenticated app page is visible", () => {
    expect(NOTIFICATION_REFRESH_INTERVAL_MS).toBe(60_000);
    expect(shouldAutoRefreshNotifications("app", { id: 1 }, "visible")).toBe(true);
    expect(shouldAutoRefreshNotifications("app", { id: 1 }, "hidden")).toBe(false);
    expect(shouldAutoRefreshNotifications("auth", { id: 1 }, "visible")).toBe(false);
    expect(shouldAutoRefreshNotifications("app", null, "visible")).toBe(false);
    expect(shouldAutoRefreshNotifications("app", { id: 1, role: "ADMIN" }, "visible")).toBe(false);
  });

  it("counts only unread notifications", () => {
    expect(countUnread(notifications)).toBe(2);
  });

  it("marks one notification without changing the others", () => {
    const result = markNotificationRead(notifications, 1, "2026-08-23T09:00:00Z");

    expect(result[0]).toMatchObject({ id: 1, read: true, readAt: "2026-08-23T09:00:00Z" });
    expect(result[1]).toBe(notifications[1]);
    expect(result[2]).toBe(notifications[2]);
  });

  it("marks all unread notifications and preserves existing read timestamps", () => {
    const result = markAllNotificationsRead(notifications, "2026-08-23T09:00:00Z");

    expect(countUnread(result)).toBe(0);
    expect(result[0].readAt).toBe("2026-08-23T09:00:00Z");
    expect(result[1].readAt).toBe("2026-08-23T08:00:00Z");
    expect(result[2].readAt).toBe("2026-08-23T09:00:00Z");
  });

  it("opens task notifications and project invitations with valid project targets", () => {
    expect(canOpenNotification({ projectId: 1, taskId: 9 })).toBe(true);
    expect(canOpenNotification({ projectId: 1, taskId: null })).toBe(false);
    expect(canOpenNotification({ projectId: null, taskId: 9 })).toBe(false);
    expect(canOpenNotification({ type: "PROJECT_MEMBER_ADDED", projectId: 1, taskId: null })).toBe(true);
  });

  it("labels deadline and status notification types", () => {
    expect(notificationTypeLabel("TASK_DUE_SOON")).toBe("Sắp đến hạn");
    expect(notificationTypeLabel("TASK_OVERDUE")).toBe("Quá hạn");
    expect(notificationTypeLabel("TASK_STATUS_CHANGED")).toBe("Trạng thái");
  });
});
