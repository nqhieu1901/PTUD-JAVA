import { useState } from "react";
import AdminUsersView from "./AdminUsersView.jsx";
import AiAssistantView from "./AiAssistantView.jsx";
import CalendarView from "./CalendarView.jsx";
import DashboardView from "./DashboardView.jsx";
import KanbanBoard from "./KanbanBoard.jsx";
import NotificationCenter from "./NotificationCenter.jsx";

export default function Workspace({
  user, projects, project, tasks, statuses, filters, loading, boardError, movingIds,
  notifications, notificationsLoading, notificationsError, markingNotificationIds, markingAllNotifications,
  onSelectProject, onNewProject, onNewTask, onEditTask,
  onManageAccount, onManageProject, onManageMembers, onManageWorkflow,
  onRefresh, onRefreshNotifications, onMarkNotificationRead, onMarkAllNotificationsRead, onOpenNotification,
  onAiAccepted, onSessionExpired, onFilterChange, onMove, onLogout
}) {
  const [activeView, setActiveView] = useState("board");
  if (user.role === "ADMIN") {
    return <AdminWorkspace user={user} onManageAccount={onManageAccount}
      onSessionExpired={onSessionExpired} onLogout={onLogout} />;
  }
  const canCreateTask = project?.currentUserRole === "OWNER" && project.status === "ACTIVE" && !loading;
  const views = [
    { id: "dashboard", icon: "◫", label: "Tổng quan" },
    { id: "board", icon: "▦", label: "Bảng công việc" },
    { id: "calendar", icon: "□", label: "Lịch" },
    { id: "assistant", icon: "✦", label: "Trợ lý" }
  ];
  const viewKey = `${project?.id ?? "empty"}:${activeView}`;
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar__brand"><span className="brand-mark" aria-hidden="true">S</span><span>SmartTask</span></div>
        <nav className="sidebar__nav" aria-label="Điều hướng chính">
          {views.map((view) => (
            <button key={view.id} className={`nav-item${activeView === view.id ? " is-active" : ""}`}
              type="button" aria-current={activeView === view.id ? "page" : undefined}
              onClick={() => setActiveView(view.id)}>
              <span aria-hidden="true">{view.icon}</span><span>{view.label}</span>
            </button>
          ))}
        </nav>
        <div className="sidebar__tip"><span className="tip-icon" aria-hidden="true">↗</span><strong>Mẹo nhỏ</strong>
          <p>Kéo thẻ sang cột khác hoặc dùng danh sách trạng thái trên từng thẻ.</p></div>
        <div className="user-menu">
          <button className="user-menu__profile" type="button" aria-label="Mở cài đặt tài khoản"
            title="Cài đặt tài khoản" onClick={onManageAccount}>
            <span className="avatar" aria-hidden="true">{initials(user.fullName)}</span>
            <span className="user-menu__copy"><strong>{user.fullName}</strong><small>{user.email}</small></span>
          </button>
          <button className="icon-button" type="button" aria-label="Đăng xuất" title="Đăng xuất" onClick={onLogout}>↪</button>
        </div>
      </aside>

      <main id="main-content" className="workspace" tabIndex="-1">
        <header className="workspace-header">
          <div className="project-switcher">
            <label htmlFor="project-select">Dự án hiện tại</label>
            <div className="project-select-row">
              <select id="project-select" value={project?.id || ""} disabled={!projects.length || loading || movingIds.size > 0}
                onChange={(event) => onSelectProject(Number(event.target.value))}>
                {!projects.length && <option value="">Chưa có dự án</option>}
                {projects.map((item) => <option key={item.id} value={item.id}>{item.name}{item.status === "ARCHIVED" ? " · Đã đóng" : ""}</option>)}
              </select>
              {project && <span className="role-badge">{roleLabel(project.currentUserRole)}</span>}
            </div>
            <p className="project-meta">{project ? projectMeta(project) : ""}</p>
          </div>
          <div className="header-actions">
            <NotificationCenter notifications={notifications} loading={notificationsLoading}
              error={notificationsError} markingIds={markingNotificationIds} markingAll={markingAllNotifications}
              onRefresh={onRefreshNotifications} onMarkRead={onMarkNotificationRead}
              onMarkAll={onMarkAllNotificationsRead} onOpen={(notification) => {
                setActiveView("board");
                onOpenNotification(notification);
              }} />
            <button className="icon-button icon-button--bordered refresh-button" type="button" aria-label="Tải lại bảng"
              title="Tải lại bảng" disabled={loading} onClick={onRefresh}>↻</button>
            {project && (
              <button className="button button--secondary member-button" type="button" disabled={loading}
                onClick={onManageMembers}><span aria-hidden="true">♙</span> Thành viên</button>
            )}
            {project?.currentUserRole === "OWNER" && (
              <button className={`icon-button icon-button--bordered project-settings-button${project.status === "ARCHIVED" ? " project-settings-button--restore" : ""}`}
                type="button" disabled={loading}
                aria-label={project.status === "ARCHIVED" ? "Mở lại dự án" : "Chỉnh sửa dự án"}
                title={project.status === "ARCHIVED" ? "Mở lại dự án" : "Chỉnh sửa dự án"}
                onClick={onManageProject}>⚙</button>
            )}
            <button className="button button--secondary new-project-button" type="button" onClick={onNewProject}>
              <span aria-hidden="true">+</span> Dự án
            </button>
            {project?.currentUserRole === "OWNER" && (
              <button className="button button--primary" type="button" disabled={!canCreateTask}
                title={project.status !== "ACTIVE" ? "Dự án đã đóng" : loading ? "Đang tải dữ liệu dự án" : "Tạo công việc"}
                onClick={onNewTask}>
                <span aria-hidden="true">+</span> Công việc
              </button>
            )}
          </div>
        </header>

        <nav className="mobile-view-tabs" aria-label="Chọn màn hình"
          style={{ "--mobile-view-count": views.length }}>
          {views.map((view) => (
            <button key={view.id} type="button" className={activeView === view.id ? "is-active" : ""}
              aria-current={activeView === view.id ? "page" : undefined}
              onClick={() => setActiveView(view.id)}>{view.label}</button>
          ))}
        </nav>

        <div key={viewKey} className="view-stage">
          {!project ? (
            <section className="empty-project" aria-labelledby="empty-project-heading">
              <div className="empty-illustration" aria-hidden="true"><span /><span /><span /></div>
              <p className="eyebrow">Khởi đầu đơn giản</p><h1 id="empty-project-heading">Tạo dự án đầu tiên</h1>
              <p>Gom công việc vào một nơi, sau đó kéo thả để theo dõi tiến độ.</p>
              <button className="button button--primary" type="button" onClick={onNewProject}><span aria-hidden="true">+</span> Tạo dự án</button>
            </section>
          ) : activeView === "board" ? (
            <section id="board" className="board-view" aria-label="Bảng công việc">
              <KanbanBoard tasks={tasks} statuses={statuses} user={user} project={project}
                filters={filters} loading={loading}
                error={boardError} movingIds={movingIds} onFilterChange={onFilterChange}
                onRetry={onRefresh} onMove={onMove} onEdit={onEditTask}
                onManageWorkflow={onManageWorkflow} />
            </section>
          ) : activeView === "assistant" ? (
            <AiAssistantView key={project.id} project={project} tasks={tasks}
              boardLoading={loading} boardError={boardError} onBoardRetry={onRefresh}
              onAccepted={onAiAccepted} onSessionExpired={onSessionExpired} />
          ) : loading || boardError ? (
            <ViewState loading={loading} error={boardError} onRetry={onRefresh} />
          ) : activeView === "calendar" ? (
            <CalendarView tasks={tasks} statuses={statuses} onOpenTask={onEditTask} />
          ) : (
            <DashboardView tasks={tasks} statuses={statuses} />
          )}
        </div>
      </main>
    </div>
  );
}

function AdminWorkspace({ user, onManageAccount, onSessionExpired, onLogout }) {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar__brand"><span className="brand-mark" aria-hidden="true">S</span><span>SmartTask</span></div>
        <nav className="sidebar__nav" aria-label="Điều hướng quản trị">
          <span className="nav-item is-active" aria-current="page">
            <span aria-hidden="true">♙</span><span>Quản lý tài khoản</span>
          </span>
        </nav>
        <div className="sidebar__tip"><span className="tip-icon" aria-hidden="true">i</span><strong>Phạm vi Admin</strong>
          <p>Quản trị tài khoản hệ thống, không tham gia dữ liệu dự án.</p></div>
        <div className="user-menu">
          <button className="user-menu__profile" type="button" aria-label="Mở cài đặt tài khoản"
            title="Cài đặt tài khoản" onClick={onManageAccount}>
            <span className="avatar" aria-hidden="true">{initials(user.fullName)}</span>
            <span className="user-menu__copy"><strong>{user.fullName}</strong><small>{user.email}</small></span>
          </button>
          <button className="icon-button" type="button" aria-label="Đăng xuất" title="Đăng xuất" onClick={onLogout}>↪</button>
        </div>
      </aside>

      <main id="main-content" className="workspace" tabIndex="-1">
        <header className="workspace-header admin-workspace-header">
          <div className="project-switcher">
            <label>Khu vực quản trị</label>
            <div className="project-select-row">
              <strong className="admin-workspace-title">Hệ thống SmartTask</strong>
              <span className="role-badge">Admin</span>
            </div>
            <p className="project-meta">Quản lý quyền truy cập tài khoản</p>
          </div>
          <div className="header-actions">
            <button className="button button--secondary" type="button" onClick={onManageAccount}>Tài khoản của tôi</button>
          </div>
        </header>
        <div className="view-stage">
          <AdminUsersView currentUser={user} onSessionExpired={onSessionExpired} />
        </div>
      </main>
    </div>
  );
}

function ViewState({ loading, error, onRetry }) {
  return (
    <section className="secondary-view-state" aria-live="polite">
      {loading ? (
        <><span className="spinner" aria-hidden="true" /><strong>Đang tải dữ liệu dự án…</strong></>
      ) : (
        <><div><strong>Chưa thể tải dữ liệu dự án</strong><p>{error}</p></div>
          <button className="button button--secondary" type="button" onClick={onRetry}>Thử lại</button></>
      )}
    </section>
  );
}

function initials(name) {
  const parts = String(name || "").trim().split(/\s+/).filter(Boolean);
  return (parts.length === 1 ? parts[0].slice(0, 2) : parts[0][0] + parts.at(-1)[0]).toLocaleUpperCase("vi");
}

function projectMeta(project) {
  if (project.status === "ARCHIVED") return "Dự án đã đóng";
  if (!project.dueDate) return "Dự án đang hoạt động";
  const [year, month, day] = project.dueDate.split("-");
  return `Hạn dự án ${day}/${month}/${year}`;
}

function roleLabel(role) {
  if (role === "OWNER") return "Chủ sở hữu";
  if (role === "VIEWER") return "Người xem";
  return "Thành viên";
}
