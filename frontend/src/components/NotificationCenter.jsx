import { useEffect, useRef, useState } from "react";
import { canOpenNotification, countUnread } from "../notifications.js";

export default function NotificationCenter({
  notifications, loading, error, markingIds, markingAll,
  onRefresh, onMarkRead, onMarkAll, onOpen
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef(null);
  const triggerRef = useRef(null);
  const unreadCount = countUnread(notifications);

  useEffect(() => {
    if (!open) return undefined;

    function handlePointerDown(event) {
      if (!rootRef.current?.contains(event.target)) setOpen(false);
    }

    function handleKeyDown(event) {
      if (event.key !== "Escape") return;
      setOpen(false);
      triggerRef.current?.focus();
    }

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  return (
    <div className="notification-center" ref={rootRef}>
      <button ref={triggerRef} className="icon-button icon-button--bordered notification-trigger" type="button"
        aria-label={unreadCount ? `Thông báo, ${unreadCount} chưa đọc` : "Thông báo"}
        aria-expanded={open} aria-controls="notification-panel"
        title="Thông báo" onClick={() => setOpen((current) => !current)}>
        <svg className="notification-bell" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" />
        </svg>
        {unreadCount > 0 && (
          <span className="notification-badge" aria-hidden="true">{unreadCount > 99 ? "99+" : unreadCount}</span>
        )}
      </button>

      {open && (
        <section id="notification-panel" className="notification-panel" aria-labelledby="notification-heading"
          aria-busy={loading}>
          <div className="notification-panel__header">
            <div>
              <h2 id="notification-heading">Thông báo</h2>
              <p>{unreadCount ? `${unreadCount} thông báo chưa đọc` : "Bạn đã đọc tất cả"}</p>
            </div>
            <button className="notification-mark-all" type="button"
              disabled={!unreadCount || loading || markingAll || markingIds.size > 0}
              onClick={onMarkAll}>{markingAll ? "Đang xử lý…" : "Đọc tất cả"}</button>
          </div>

          {error && (
            <div className="notification-error" role="alert">
              <span>{error}</span>
              <button type="button" disabled={loading} onClick={onRefresh}>Thử lại</button>
            </div>
          )}

          {loading && !notifications.length ? (
            <div className="notification-state" role="status">
              <span className="spinner" aria-hidden="true" />
              <span>Đang tải thông báo…</span>
            </div>
          ) : notifications.length ? (
            <ul className="notification-list">
              {notifications.map((notification) => {
                const isMarking = markingIds.has(notification.id);
                const canOpen = canOpenNotification(notification);
                return (
                  <li key={notification.id} className={`notification-item${notification.read ? "" : " is-unread"}`}>
                    <span className="notification-item__dot" aria-hidden="true" />
                    <div className="notification-item__content">
                      <div className="notification-item__meta">
                        <span>{notificationTypeLabel(notification.type)}</span>
                        <time dateTime={notification.createdAt}>{formatDateTime(notification.createdAt)}</time>
                      </div>
                      <strong>{notification.title}</strong>
                      <p>{notification.message}</p>
                      <div className="notification-item__actions">
                        {canOpen && (
                          <button type="button" onClick={() => { setOpen(false); onOpen(notification); }}>
                            {notification.taskId ? "Mở công việc" : "Mở dự án"}
                          </button>
                        )}
                        {!notification.read && (
                          <button type="button" disabled={isMarking || markingAll}
                            onClick={() => onMarkRead(notification.id)}>
                            {isMarking ? "Đang đánh dấu…" : "Đánh dấu đã đọc"}
                          </button>
                        )}
                      </div>
                    </div>
                  </li>
                );
              })}
            </ul>
          ) : !error && (
            <div className="notification-state">
              <span className="notification-empty-icon" aria-hidden="true">✓</span>
              <strong>Chưa có thông báo</strong>
              <span>Các cập nhật mới sẽ xuất hiện tại đây.</span>
            </div>
          )}
        </section>
      )}
    </div>
  );
}

export function notificationTypeLabel(type) {
  if (type === "PROJECT_MEMBER_ADDED") return "Dự án";
  if (type === "TASK_ASSIGNED") return "Công việc";
  if (type === "TASK_DUE_SOON") return "Sắp đến hạn";
  if (type === "TASK_OVERDUE") return "Quá hạn";
  if (type === "TASK_STATUS_CHANGED") return "Trạng thái";
  return "Cập nhật";
}

function formatDateTime(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit"
  }).format(date);
}
