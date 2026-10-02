import { useEffect, useMemo, useState } from "react";
import { appendPosition, isAdjacentStatusTransition, PRIORITY, statusColor, tasksInStatus } from "../board.js";

export default function KanbanBoard({
  tasks, statuses = [], user, project, filters, loading, error, movingIds,
  onFilterChange, onRetry, onMove, onEdit, onManageWorkflow
}) {
  const [draggingId, setDraggingId] = useState(null);
  const [dropTarget, setDropTarget] = useState(null);
  const [settlingTask, setSettlingTask] = useState(null);
  const filtersActive = Boolean(filters.query || filters.priority);
  const workflowStatuses = statuses.length
    ? statuses
    : [...new Map(tasks.map((task) => [task.status.code, task.status])).values()];
  const visibleTasks = useMemo(() => tasks.filter((task) => {
    const haystack = `${task.title} ${task.description || ""} ${task.assignee?.fullName || ""}`.toLocaleLowerCase("vi");
    return (!filters.priority || task.priority === filters.priority)
      && (!filters.query || haystack.includes(filters.query.toLocaleLowerCase("vi")));
  }), [tasks, filters]);

  useEffect(() => {
    if (!settlingTask || movingIds.size > 0) return undefined;
    const timer = window.setTimeout(() => setSettlingTask(null), 720);
    return () => window.clearTimeout(timer);
  }, [settlingTask, movingIds]);

  function canMove(task) {
    return project.status === "ACTIVE"
      && project.currentUserRole !== "VIEWER"
      && (project.currentUserRole === "OWNER" || task.assignee?.id === user.id);
  }

  function startDrag(event, task) {
    if (event.target.closest("button, select") || !canMove(task) || filtersActive || movingIds.size > 0) {
      event.preventDefault();
      return;
    }
    setDraggingId(task.id);
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData("text/plain", String(task.id));
  }

  function dragOver(event, status) {
    if (!draggingId || filtersActive) return;
    const draggingTask = tasks.find((task) => task.id === draggingId);
    if (!draggingTask || !isAdjacentStatusTransition(workflowStatuses, draggingTask.status.code, status)) {
      setDropTarget(null);
      return;
    }
    event.preventDefault();
    event.dataTransfer.dropEffect = "move";
    const cards = [...event.currentTarget.querySelectorAll(".task-card")]
      .filter((card) => Number(card.dataset.taskId) !== draggingId);
    const firstAfterPointer = cards.findIndex((card) => {
      const box = card.getBoundingClientRect();
      return event.clientY < box.top + box.height / 2;
    });
    setDropTarget({ status, index: firstAfterPointer < 0 ? cards.length : firstAfterPointer });
  }

  function drop(event, status) {
    event.preventDefault();
    const taskId = draggingId || Number(event.dataTransfer.getData("text/plain"));
    const movingTask = tasks.find((task) => task.id === taskId);
    if (!movingTask || !isAdjacentStatusTransition(workflowStatuses, movingTask.status.code, status)) {
      setDraggingId(null);
      setDropTarget(null);
      return;
    }
    const boardOrder = dropTarget?.status === status
      ? dropTarget.index
      : appendPosition(tasks.filter((task) => task.id !== taskId), status);
    setDraggingId(null);
    setDropTarget(null);
    if (taskId) moveWithMotion(taskId, status, boardOrder);
  }

  function moveWithMotion(taskId, status, boardOrder) {
    setSettlingTask({ id: taskId, key: Date.now() });
    onMove(taskId, status, boardOrder);
  }

  if (loading) {
    return (
      <div className="board-grid board-grid--loading" aria-label="Đang tải công việc">
        {[3, 2, 3].map((count, column) => (
          <div className="skeleton-column" key={column}><i />{Array.from({ length: count }, (_, index) => <span key={index} />)}</div>
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="board-error" role="alert">
        <div><strong>Chưa thể tải bảng công việc</strong><p>{error}</p></div>
        <button className="button button--secondary" type="button" onClick={onRetry}>Thử lại</button>
      </div>
    );
  }

  const done = tasks.filter((task) => task.status.terminal).length;
  return (
    <>
      <div className="board-heading">
        <div>
          <p className="eyebrow">{project.name} · Kanban</p>
          <h1>{project.name}</h1>
          <p className="board-summary">{tasks.length} công việc · {done} đã hoàn thành</p>
        </div>
        <div className="board-heading__tools">
          {project.currentUserRole === "OWNER" && project.status === "ACTIVE" && (
            <button className="button button--secondary button--compact add-column-button" type="button"
              disabled={movingIds.size > 0} onClick={onManageWorkflow}>
              <span aria-hidden="true">+</span> Thêm / quản lý cột
            </button>
          )}
          <div className="board-filters" aria-label="Bộ lọc công việc">
            <label className="search-field">
              <span aria-hidden="true">⌕</span><span className="sr-only">Tìm công việc</span>
              <input type="search" value={filters.query} placeholder="Tìm công việc…"
                onChange={(event) => onFilterChange({ ...filters, query: event.target.value })} />
            </label>
            <label className="compact-select"><span className="sr-only">Lọc theo ưu tiên</span>
              <select value={filters.priority} onChange={(event) => onFilterChange({ ...filters, priority: event.target.value })}>
                <option value="">Mọi ưu tiên</option>
                {Object.entries(PRIORITY).reverse().map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </label>
          </div>
        </div>
      </div>

      {project.status === "ARCHIVED" && <div className="board-notice" role="status">Dự án đã đóng. Bảng hiện ở chế độ chỉ xem.</div>}
      {project.status === "ACTIVE" && project.currentUserRole === "VIEWER" && (
        <div className="board-notice" role="status">Bạn đang có quyền Người xem. Có thể mở chi tiết công việc nhưng không thể thay đổi bảng.</div>
      )}
      {filtersActive && <div className="board-notice" role="status">Đang lọc công việc. Xóa bộ lọc để sắp xếp bằng kéo-thả.</div>}

      <div className="board-grid" aria-label="Bảng Kanban"
        style={{ "--workflow-columns": Math.max(workflowStatuses.length, 1) }}>
        {workflowStatuses.map((status, statusIndex) => {
          const columnTasks = tasksInStatus(visibleTasks, status.code);
          return (
            <section key={status.id ?? status.code} className={`kanban-column${dropTarget?.status === status.code ? " is-drag-over" : ""}`}
              data-status={status.code} aria-labelledby={`workflow-status-${status.id ?? status.code}-heading`}>
              <header className="column-header">
                <span className="status-dot" style={{ backgroundColor: statusColor(status, statusIndex) }} aria-hidden="true" />
                <h2 id={`workflow-status-${status.id ?? status.code}-heading`}>{status.displayName}</h2><span className="count-badge">{columnTasks.length}</span>
              </header>
              <div className="task-list" role="list" onDragOver={(event) => dragOver(event, status.code)}
                onDrop={(event) => drop(event, status.code)}>
                {columnTasks.length === 0 ? <EmptyColumn terminal={status.terminal} /> : columnTasks.map((task, taskIndex) => (
                  <TaskCard key={task.id} task={task} movable={canMove(task)}
                    statuses={workflowStatuses}
                    parentTitle={tasks.find((candidate) => candidate.id === task.parentTaskId)?.title}
                    editable={project.currentUserRole === "OWNER" && project.status === "ACTIVE"}
                    draggable={canMove(task) && !filtersActive && movingIds.size === 0}
                    moving={movingIds.size > 0} dragging={draggingId === task.id}
                    settling={settlingTask?.id === task.id}
                    dropBefore={dropTarget?.status === status.code && dropTarget.index === taskIndex}
                    onDragStart={(event) => startDrag(event, task)}
                    onDragEnd={() => { setDraggingId(null); setDropTarget(null); }}
                    onEdit={() => onEdit(task)}
                    onStatusChange={(targetStatus) => moveWithMotion(task.id, targetStatus, appendPosition(tasks, targetStatus))} />
                ))}
              </div>
            </section>
          );
        })}
      </div>
    </>
  );
}

function TaskCard({
  task, statuses, parentTitle, movable, editable, draggable, moving, dragging, settling, dropBefore,
  onDragStart, onDragEnd, onEdit, onStatusChange
}) {
  const overdue = !task.status.terminal && new Date(task.dueAt).getTime() < Date.now();
  return (
    <article className={`task-card${dragging ? " is-dragging" : ""}${settling ? " is-settling" : ""}${dropBefore ? " is-drop-before" : ""}`} data-task-id={task.id}
      data-priority={task.priority} role="listitem" draggable={draggable}
      onDragStart={onDragStart} onDragEnd={onDragEnd} aria-busy={moving}>
      {task.status.terminal && (
        <span className={`task-complete-stamp${settling ? " is-pressing" : ""}`} aria-hidden="true">完</span>
      )}
      <div className="task-card__top">
        <span className={`priority-badge priority-badge--${task.priority}`}>{PRIORITY[task.priority]}</span>
        <span className="task-card__actions">
          <button className="task-edit-button" type="button" disabled={moving} draggable={false}
            aria-label={`${editable ? "Sửa" : "Xem"} công việc ${task.title}`}
            title={editable ? "Sửa công việc" : "Xem chi tiết và bình luận"} onClick={onEdit}>{editable ? "✎" : "↗"}</button>
          {draggable && <span className="drag-handle" aria-hidden="true">⠿</span>}
        </span>
      </div>
      {task.parentTaskId && <span className="task-parent" title={parentTitle || "Công việc cha"}>↳ {parentTitle || `Công việc #${task.parentTaskId}`}</span>}
      <h3>{task.title}</h3>
      {task.description && <p className="task-card__description">{task.description}</p>}
      <div className="task-card__meta">
        <time className={`due-date${overdue ? " is-overdue" : ""}`} dateTime={task.dueAt}>{formatDateTime(task.dueAt)}</time>
        <span className={`task-assignee${task.assignee ? "" : " is-empty"}`}
          title={task.assignee?.fullName || "Chưa phân công"}>{task.assignee ? initials(task.assignee.fullName) : "–"}</span>
        <select className="task-status-select" value={task.status.code} disabled={!movable || moving}
          aria-label={`Trạng thái của ${task.title}`} onChange={(event) => onStatusChange(event.target.value)}>
          {statuses.map((status) => (
            <option key={status.id ?? status.code} value={status.code}
              disabled={!isAdjacentStatusTransition(statuses, task.status.code, status.code)}>{status.displayName}</option>
          ))}
        </select>
      </div>
    </article>
  );
}

function EmptyColumn({ terminal }) {
  return <div className="task-empty"><span><i aria-hidden="true">{terminal ? "✓" : "+"}</i>Chưa có công việc</span></div>;
}

function initials(name) {
  const parts = String(name || "").trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return "?";
  return (parts.length === 1 ? parts[0].slice(0, 2) : parts[0][0] + parts.at(-1)[0]).toLocaleUpperCase("vi");
}

function formatDateTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Chưa có hạn";
  return new Intl.DateTimeFormat("vi-VN", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" }).format(date);
}
