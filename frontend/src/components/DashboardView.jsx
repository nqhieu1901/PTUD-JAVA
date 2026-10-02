import { deadlineTasks, summarizeTasks } from "../planning.js";
import { statusColor } from "../board.js";

const PRIORITY_LABELS = {
  URGENT: "Khẩn cấp",
  HIGH: "Cao",
  MEDIUM: "Trung bình",
  LOW: "Thấp"
};

export default function DashboardView({ tasks, statuses = [] }) {
  const workflow = statuses.length ? statuses : uniqueTaskStatuses(tasks);
  const summary = summarizeTasks(tasks, new Date(), workflow);
  const deadlines = deadlineTasks(tasks);

  return (
    <section className="insight-view" aria-labelledby="dashboard-heading">
      <div className="view-heading">
        <div><p className="eyebrow">Tổng quan dự án</p><h1 id="dashboard-heading">Dashboard</h1></div>
        <p>Theo dõi tiến độ, khối lượng và những deadline cần chú ý.</p>
      </div>

      <div className="metric-grid">
        <Metric label="Tổng công việc" value={summary.total} detail={`${summary.estimatedMinutes} phút ước tính`} />
        <Metric label="Đã hoàn thành" value={summary.completed} detail={`${summary.completionRate}% toàn dự án`} tone="teal" />
        <Metric label="Đã quá hạn" value={summary.overdue} detail="Chưa hoàn thành" tone={summary.overdue ? "danger" : "neutral"} />
        <Metric label="Trong 7 ngày" value={summary.dueSoon} detail="Deadline sắp tới" tone="gold" />
      </div>

      <div className="insight-grid">
        <article className="insight-card">
          <div className="insight-card__header"><h2>Tiến độ trạng thái</h2><strong>{summary.completionRate}%</strong></div>
          <div className="progress-track" aria-label={`Đã hoàn thành ${summary.completionRate}%`}>
            <span style={{ width: `${summary.completionRate}%` }} />
          </div>
          <div className="status-breakdown">
            {workflow.map((status, index) => (
              <div key={status.code}><span className="status-dot" style={{ backgroundColor: statusColor(status, index) }} />
                <span>{status.displayName}</span><strong>{summary.byStatus[status.code] || 0}</strong></div>
            ))}
          </div>
        </article>

        <article className="insight-card">
          <div className="insight-card__header"><h2>Mức độ ưu tiên</h2></div>
          <div className="priority-breakdown">
            {Object.entries(PRIORITY_LABELS).map(([priority, label]) => (
              <div key={priority}><span>{label}</span><strong>{summary.byPriority[priority]}</strong>
                <span className="priority-bar"><i className={`priority-bar__fill priority-bar__fill--${priority.toLowerCase()}`}
                  style={{ width: `${summary.total ? summary.byPriority[priority] / summary.total * 100 : 0}%` }} /></span></div>
            ))}
          </div>
        </article>

        <article className="insight-card insight-card--wide">
          <div className="insight-card__header"><h2>Deadline gần nhất</h2><span>{deadlines.length ? `${deadlines.length} công việc` : "Không có"}</span></div>
          {!deadlines.length ? <p className="quiet-empty">Chưa có công việc nào cần theo dõi deadline.</p> : (
            <div className="deadline-list">
              {deadlines.map((task) => (
                <div key={task.id} className="deadline-row">
                  <span className={`priority-pill priority-pill--${task.priority.toLowerCase()}`}>{PRIORITY_LABELS[task.priority]}</span>
                  <div><strong>{task.title}</strong><small>{task.assignee?.fullName || "Chưa giao người phụ trách"}</small></div>
                  <time className={task.isOverdue ? "is-overdue" : ""} dateTime={task.dueAt}>{formatDateTime(task.dueAt)}</time>
                </div>
              ))}
            </div>
          )}
        </article>
      </div>
    </section>
  );
}

function uniqueTaskStatuses(tasks) {
  return [...new Map(tasks.map((task) => [task.status.code, task.status])).values()];
}

function Metric({ label, value, detail, tone = "neutral" }) {
  return <article className={`metric-card metric-card--${tone}`}><span>{label}</span><strong>{value}</strong><small>{detail}</small></article>;
}

function formatDateTime(value) {
  return new Intl.DateTimeFormat("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}
