export const MIN_AI_SUGGESTIONS = 2;
export const MAX_AI_SUGGESTIONS = 6;
export const DEFAULT_AI_SUGGESTIONS = 3;

export function normalizeSuggestionCount(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return DEFAULT_AI_SUGGESTIONS;
  return Math.min(MAX_AI_SUGGESTIONS, Math.max(MIN_AI_SUGGESTIONS, Math.trunc(parsed)));
}

export function canManageAi(project) {
  return project?.currentUserRole === "OWNER" && project.status === "ACTIVE";
}

export function chooseSourceTaskId(tasks, currentTaskId) {
  if (!Array.isArray(tasks) || tasks.length === 0) return currentTaskId ?? null;
  return tasks.some((task) => task.id === currentTaskId) ? currentTaskId : tasks[0].id;
}

export function replaceAiSuggestion(decomposition, updatedSuggestion) {
  if (!decomposition || !updatedSuggestion) return decomposition;
  return {
    ...decomposition,
    suggestions: (decomposition.suggestions || []).map((suggestion) =>
      suggestion.id === updatedSuggestion.id ? updatedSuggestion : suggestion)
  };
}
