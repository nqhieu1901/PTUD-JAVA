export const PRIORITY = {
  LOW: "Thấp",
  MEDIUM: "Trung bình",
  HIGH: "Cao",
  URGENT: "Khẩn cấp"
};

export function compareBoardOrder(first, second) {
  return first.boardOrder - second.boardOrder || first.id - second.id;
}

export function tasksInStatus(tasks, status) {
  return tasks.filter((task) => task.status.code === status).sort(compareBoardOrder);
}

export function appendPosition(tasks, status) {
  return tasks.filter((task) => task.status.code === status).length;
}

export function statusColor(status, index = 0) {
  if (status?.terminal) return "#3c756b";
  return ["#788590", "#b58a46", "#55728f", "#806c7b", "#d4553d", "#66847c"][index % 6];
}

export function isAdjacentStatusTransition(statuses, sourceCode, targetCode) {
  if (sourceCode === targetCode) return true;
  const ordered = [...statuses].sort((first, second) => first.displayOrder - second.displayOrder);
  const sourceIndex = ordered.findIndex((status) => status.code === sourceCode);
  const targetIndex = ordered.findIndex((status) => status.code === targetCode);
  return sourceIndex >= 0 && targetIndex >= 0 && Math.abs(sourceIndex - targetIndex) === 1;
}

export function moveTaskLocally(tasks, taskId, targetStatus, requestedOrder, statuses = []) {
  const moving = tasks.find((task) => task.id === taskId);
  if (!moving) return tasks;

  const sourceStatus = moving.status.code;
  const statusCodes = [...new Set([
    ...statuses.map((status) => status.code),
    ...tasks.map((task) => task.status.code),
    sourceStatus,
    targetStatus
  ])];
  const columns = Object.fromEntries(
    statusCodes.map((status) => [status, tasksInStatus(tasks, status).filter((task) => task.id !== taskId)])
  );
  const target = columns[targetStatus];
  const targetIndex = Math.max(0, Math.min(requestedOrder, target.length));
  const targetConfig = statuses.find((status) => status.code === targetStatus);
  target.splice(targetIndex, 0, {
    ...moving,
    status: { ...moving.status, ...targetConfig, code: targetStatus }
  });

  const changedStatuses = new Set([sourceStatus, targetStatus]);
  const movedById = new Map();
  changedStatuses.forEach((status) => {
    columns[status].forEach((task, index) => movedById.set(task.id, { ...task, boardOrder: index }));
  });
  return tasks.map((task) => movedById.get(task.id) || task);
}
