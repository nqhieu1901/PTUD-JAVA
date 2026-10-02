export const NOTIFICATION_REFRESH_INTERVAL_MS = 60_000;

export function shouldAutoRefreshNotifications(phase, user, visibilityState) {
  return phase === "app" && Boolean(user) && user.role !== "ADMIN" && visibilityState === "visible";
}

export function countUnread(notifications) {
  return notifications.reduce((count, notification) => count + (notification.read ? 0 : 1), 0);
}

export function markNotificationRead(notifications, notificationId, readAt) {
  return notifications.map((notification) => notification.id === notificationId
    ? { ...notification, read: true, readAt: notification.readAt || readAt }
    : notification);
}

export function markAllNotificationsRead(notifications, readAt) {
  return notifications.map((notification) => notification.read
    ? notification
    : { ...notification, read: true, readAt: notification.readAt || readAt });
}

export function canOpenNotification(notification) {
  return Boolean(notification?.projectId
    && (notification.taskId || notification.type === "PROJECT_MEMBER_ADDED"));
}
