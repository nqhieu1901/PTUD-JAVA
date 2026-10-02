const PRIORITIES = ["LOW", "MEDIUM", "HIGH", "URGENT"];

export function summarizeTasks(tasks, now = new Date(), statuses = []) {
  const statusCodes = [...new Set([
    ...statuses.map((status) => status.code),
    ...tasks.map((task) => task.status?.code).filter(Boolean)
  ])];
  const summary = {
    total: tasks.length,
    completed: 0,
    completionRate: 0,
    overdue: 0,
    dueSoon: 0,
    estimatedMinutes: 0,
    byStatus: Object.fromEntries(statusCodes.map((status) => [status, 0])),
    byPriority: Object.fromEntries(PRIORITIES.map((priority) => [priority, 0]))
  };
  const dueSoonLimit = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);

  for (const task of tasks) {
    const status = task.status?.code;
    if (status in summary.byStatus) summary.byStatus[status] += 1;
    if (task.priority in summary.byPriority) summary.byPriority[task.priority] += 1;
    if (Number.isFinite(task.estimatedMinutes)) summary.estimatedMinutes += task.estimatedMinutes;

    if (isTerminal(task)) {
      summary.completed += 1;
      continue;
    }

    const dueAt = parseDate(task.dueAt);
    if (!dueAt) continue;
    if (dueAt < now) summary.overdue += 1;
    else if (dueAt <= dueSoonLimit) summary.dueSoon += 1;
  }

  summary.completionRate = summary.total
    ? Math.round((summary.completed / summary.total) * 100)
    : 0;
  return summary;
}

export function deadlineTasks(tasks, now = new Date(), limit = 6) {
  return tasks
    .filter((task) => !isTerminal(task) && parseDate(task.dueAt))
    .map((task) => ({ ...task, isOverdue: parseDate(task.dueAt) < now }))
    .sort((left, right) => parseDate(left.dueAt) - parseDate(right.dueAt))
    .slice(0, limit);
}

export function isTerminal(task) {
  return task.status?.terminal ?? task.status?.code === "DONE";
}

export function tasksByDueDate(tasks) {
  return tasks.reduce((groups, task) => {
    const dateKey = typeof task.dueAt === "string" ? task.dueAt.slice(0, 10) : "";
    if (!/^\d{4}-\d{2}-\d{2}$/.test(dateKey)) return groups;
    (groups[dateKey] ||= []).push(task);
    groups[dateKey].sort((left, right) => String(left.dueAt).localeCompare(String(right.dueAt)));
    return groups;
  }, {});
}

export function monthGrid(year, monthIndex) {
  const firstDay = new Date(year, monthIndex, 1, 12);
  const mondayOffset = (firstDay.getDay() + 6) % 7;
  const gridStart = new Date(year, monthIndex, 1 - mondayOffset, 12);

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(gridStart);
    date.setDate(gridStart.getDate() + index);
    return {
      key: localDateKey(date),
      day: date.getDate(),
      inMonth: date.getMonth() === monthIndex,
      isToday: localDateKey(date) === localDateKey(new Date())
    };
  });
}

export function localDateKey(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function parseDate(value) {
  if (!value) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}
