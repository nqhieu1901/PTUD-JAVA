import { useMemo, useState } from "react";
import { statusColor } from "../board.js";
import { localDateKey, monthGrid, tasksByDueDate } from "../planning.js";

const WEEKDAYS = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"];
export default function CalendarView({ tasks, statuses = [], onOpenTask }) {
  const [cursor, setCursor] = useState(() => startOfMonth(new Date()));
  const [expandedDay, setExpandedDay] = useState(null);
  const workflow = statuses.length ? statuses : uniqueTaskStatuses(tasks);
  const statusIndex = new Map(workflow.map((status, index) => [status.code, index]));
  const groupedTasks = useMemo(() => tasksByDueDate(tasks), [tasks]);
  const days = useMemo(() => monthGrid(cursor.getFullYear(), cursor.getMonth()), [cursor]);
  const title = new Intl.DateTimeFormat("vi-VN", { month: "long", year: "numeric" }).format(cursor);

  function changeMonth(offset) {
    setExpandedDay(null);
    setCursor((current) => new Date(current.getFullYear(), current.getMonth() + offset, 1, 12));
  }

  return (
    <section className="calendar-view" aria-labelledby="calendar-heading">
      <div className="view-heading view-heading--calendar">
        <div><p className="eyebrow">Kế hoạch theo thời gian</p><h1 id="calendar-heading">Lịch công việc</h1></div>
        <div className="calendar-actions">
          <button className="button button--secondary button--compact" type="button" onClick={() => setCursor(startOfMonth(new Date()))}>Hôm nay</button>
          <div className="calendar-pager">
            <button className="icon-button icon-button--bordered" type="button" aria-label="Tháng trước" onClick={() => changeMonth(-1)}>‹</button>
            <strong aria-live="polite">{capitalize(title)}</strong>
            <button className="icon-button icon-button--bordered" type="button" aria-label="Tháng sau" onClick={() => changeMonth(1)}>›</button>
          </div>
        </div>
      </div>

      <div className="calendar-shell">
        <div className="calendar-weekdays" aria-hidden="true">
          {WEEKDAYS.map((day) => <span key={day}>{day}</span>)}
        </div>
        <div className="calendar-grid">
          {days.map((day) => {
            const dateTasks = groupedTasks[day.key] || [];
            return (
              <article key={day.key} className={`calendar-day${day.inMonth ? "" : " is-outside"}${day.isToday ? " is-today" : ""}`}>
                <time dateTime={day.key}>{day.day}</time>
                <div className="calendar-day__tasks">
                  {(expandedDay === day.key ? dateTasks : dateTasks.slice(0, 3)).map((task) => (
                    <button type="button" key={task.id} className="calendar-task" onClick={() => onOpenTask?.(task)}
                      style={{ borderLeftColor: statusColor(task.status, statusIndex.get(task.status.code) || 0), opacity: task.status.terminal ? .72 : 1 }}
                      title={`${task.title} · ${task.status.displayName}`}>
                      <span>{task.title}</span><small>{formatTime(task.dueAt)}</small>
                    </button>
                  ))}
                  {dateTasks.length > 3 && <button type="button" className="calendar-more"
                    aria-expanded={expandedDay === day.key}
                    aria-label={`${expandedDay === day.key ? "Thu gọn" : "Xem tất cả công việc ngày"} ${day.key}`}
                    onClick={() => setExpandedDay((current) => current === day.key ? null : day.key)}>
                    {expandedDay === day.key ? "Thu gọn" : `+${dateTasks.length - 3} công việc`}
                  </button>}
                </div>
              </article>
            );
          })}
        </div>
      </div>

      <div className="calendar-legend">
        {workflow.map((status, index) => <span key={status.code}>
          <i className="status-dot" style={{ backgroundColor: statusColor(status, index) }} />{status.displayName}</span>)}
        <small>{tasks.length} công việc trong dự án</small>
      </div>
    </section>
  );
}

function uniqueTaskStatuses(tasks) {
  return [...new Map(tasks.map((task) => [task.status.code, task.status])).values()];
}

function startOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth(), 1, 12);
}

function formatTime(value) {
  return new Intl.DateTimeFormat("vi-VN", { hour: "2-digit", minute: "2-digit" }).format(new Date(value));
}

function capitalize(value) {
  return value.charAt(0).toLocaleUpperCase("vi") + value.slice(1);
}
