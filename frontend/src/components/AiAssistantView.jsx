import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../api.js";
import {
  canManageAi,
  chooseSourceTaskId,
  DEFAULT_AI_SUGGESTIONS,
  normalizeSuggestionCount,
  replaceAiSuggestion
} from "../ai-assistant.js";

export default function AiAssistantView({
  project, tasks, boardLoading, boardError, onBoardRetry, onAccepted, onSessionExpired
}) {
  const requestVersion = useRef(0);
  const mounted = useRef(true);
  const [sourceTaskId, setSourceTaskId] = useState(null);
  const [maxSuggestions, setMaxSuggestions] = useState(DEFAULT_AI_SUGGESTIONS);
  const [decomposition, setDecomposition] = useState(null);
  const [latestLoading, setLatestLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [decisionBusyId, setDecisionBusyId] = useState(null);
  const [error, setError] = useState("");

  const projectId = project.id;
  const sourceTask = tasks.find((task) => task.id === sourceTaskId) || null;
  const canManage = canManageAi(project);
  const busy = generating || decisionBusyId !== null;

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      requestVersion.current += 1;
    };
  }, []);

  useEffect(() => {
    setSourceTaskId((current) => chooseSourceTaskId(tasks, current));
  }, [tasks]);

  const loadLatest = useCallback(async (targetTaskId) => {
    if (!targetTaskId) return;
    const version = ++requestVersion.current;
    setLatestLoading(true);
    setError("");
    setDecomposition(null);
    try {
      const response = await api(`/api/projects/${projectId}/tasks/${targetTaskId}/ai/decompositions/latest`);
      if (mounted.current && version === requestVersion.current) setDecomposition(response);
    } catch (requestError) {
      if (!mounted.current || version !== requestVersion.current) return;
      if (requestError.status === 401) onSessionExpired();
      else if (requestError.status === 404) setDecomposition(null);
      else setError(aiErrorMessage(requestError, "Không thể tải gợi ý gần nhất."));
    } finally {
      if (mounted.current && version === requestVersion.current) setLatestLoading(false);
    }
  }, [onSessionExpired, projectId]);

  useEffect(() => {
    if (sourceTask) loadLatest(sourceTask.id);
  }, [loadLatest, sourceTask?.id]);

  async function generateSuggestions() {
    if (!canManage || !sourceTask || busy || boardLoading) return;
    const targetTaskId = sourceTask.id;
    const version = ++requestVersion.current;
    setGenerating(true);
    setLatestLoading(false);
    setError("");
    setDecomposition(null);
    try {
      const response = await api(`/api/projects/${projectId}/tasks/${targetTaskId}/ai/decompositions`, {
        method: "POST",
        body: { maxSuggestions: normalizeSuggestionCount(maxSuggestions) }
      });
      if (mounted.current && version === requestVersion.current) setDecomposition(response);
    } catch (requestError) {
      if (!mounted.current || version !== requestVersion.current) return;
      if (requestError.status === 401) onSessionExpired();
      else setError(aiErrorMessage(requestError, "Không thể tạo gợi ý."));
    } finally {
      if (mounted.current && version === requestVersion.current) setGenerating(false);
    }
  }

  async function decideSuggestion(suggestion, decision) {
    if (!canManage || suggestion.decisionStatus !== "PENDING" || busy || boardLoading) return;
    setDecisionBusyId(suggestion.id);
    setError("");
    try {
      const updated = await api(`/api/projects/${projectId}/ai/suggestions/${suggestion.id}/${decision}`, {
        method: "POST"
      });
      if (!mounted.current) return;
      setDecomposition((current) => replaceAiSuggestion(current, updated));
      if (decision === "accept") await onAccepted(projectId, updated);
    } catch (requestError) {
      if (!mounted.current) return;
      if (requestError.status === 401) onSessionExpired();
      else setError(aiErrorMessage(requestError, "Không thể cập nhật gợi ý."));
    } finally {
      if (mounted.current) setDecisionBusyId(null);
    }
  }

  return (
    <section className="assistant-view" aria-labelledby="assistant-heading">
      <header className="view-heading assistant-heading">
        <div><p className="eyebrow">Lập kế hoạch có kiểm soát</p><h1 id="assistant-heading">Trợ lý chia nhỏ công việc</h1></div>
        <span className="ai-demo-badge">AI</span>
      </header>

      <div className="ai-demo-notice" role="note">
        <span aria-hidden="true">◇</span>
        <div><strong>Trợ lý lập kế hoạch có kiểm soát</strong>
          <p>Nguồn tạo gợi ý được hiển thị trong từng kết quả. Gợi ý chỉ trở thành công việc thật sau khi Chủ sở hữu chấp nhận.</p></div>
      </div>

      <div className="ai-controls">
        <label className="field ai-source-field">
          <span>Công việc cần chia nhỏ</span>
          <select value={sourceTaskId ?? ""} disabled={boardLoading || busy || tasks.length === 0}
            onChange={(event) => setSourceTaskId(Number(event.target.value))}>
            {tasks.length === 0 && <option value={sourceTaskId ?? ""}>{boardLoading ? "Đang tải công việc…" : "Chưa có công việc"}</option>}
            {tasks.map((task) => (
              <option key={task.id} value={task.id}>{task.parentTaskId ? "↳ " : ""}{task.title}</option>
            ))}
          </select>
        </label>
        <label className="field ai-count-field">
          <span>Số gợi ý</span>
          <select value={maxSuggestions} disabled={!canManage || boardLoading || busy}
            onChange={(event) => setMaxSuggestions(normalizeSuggestionCount(event.target.value))}>
            {[2, 3, 4, 5, 6].map((count) => <option key={count} value={count}>{count}</option>)}
          </select>
        </label>
        <button className="button button--primary ai-generate-button" type="button"
          disabled={!canManage || !sourceTask || boardLoading || busy || latestLoading}
          title={manageHint(project)} onClick={generateSuggestions}>
          {generating ? <><span className="spinner spinner--small" aria-hidden="true" /> Đang tạo…</> : "Tạo gợi ý"}
        </button>
      </div>

      {!canManage && (
        <p className="board-notice">{project.status !== "ACTIVE"
          ? "Dự án đã đóng. Bạn vẫn có thể xem gợi ý đã tạo nhưng không thể tạo hoặc duyệt gợi ý mới."
          : project.currentUserRole === "VIEWER"
            ? "Người xem chỉ có thể đọc gợi ý. Chỉ Chủ sở hữu dự án mới có thể tạo, chấp nhận hoặc từ chối."
            : "Thành viên có thể xem gợi ý. Chỉ Chủ sở hữu dự án mới có thể tạo, chấp nhận hoặc từ chối."}</p>
      )}
      {boardError && (
        <div className="board-error" role="alert"><div><strong>Chưa thể tải danh sách công việc</strong><p>{boardError}</p></div>
          <button className="button button--secondary" type="button" onClick={onBoardRetry}>Thử lại</button></div>
      )}
      {error && (
        <div className="board-error" role="alert"><div><strong>Trợ lý chưa thể hoàn tất yêu cầu</strong><p>{error}</p></div>
          {sourceTask && <button className="button button--secondary" type="button" disabled={busy}
            onClick={() => loadLatest(sourceTask.id)}>Tải lại</button>}</div>
      )}

      {latestLoading ? (
        <AssistantState loading text="Đang tải gợi ý gần nhất…" />
      ) : generating ? (
        <AssistantState loading text="Đang phân tích công việc…" />
      ) : decomposition ? (
        <DecompositionResult decomposition={decomposition} canManage={canManage}
          decisionBusyId={decisionBusyId} onDecide={decideSuggestion} />
      ) : (
        <AssistantState text={emptyText(tasks, sourceTask, boardLoading, canManage)} />
      )}
    </section>
  );
}

function DecompositionResult({ decomposition, canManage, decisionBusyId, onDecide }) {
  const suggestions = decomposition.status === "SUCCEEDED" ? decomposition.suggestions || [] : [];
  return (
    <section className="ai-result" aria-live="polite">
      <header className="ai-result__header">
        <div><p className="eyebrow">Lần tạo #{decomposition.requestId}</p><h2>Gợi ý gần nhất</h2>
          <p>{decomposition.provider} · {decomposition.modelName} · {formatDateTime(decomposition.finishedAt || decomposition.createdAt)}</p></div>
        <span className={`ai-request-status ai-request-status--${String(decomposition.status).toLowerCase()}`}>
          {requestStatusLabel(decomposition.status)}</span>
      </header>
      <div className="ai-response-notice"><span className="ai-demo-badge">{decomposition.generationMode || "AI"}</span>
        <p>{decomposition.notice || "Hãy rà soát nội dung trước khi chấp nhận gợi ý."}</p></div>
      <div className="ai-suggestion-grid">
        {suggestions.map((suggestion, index) => (
          <SuggestionCard key={suggestion.id} suggestion={suggestion} index={index}
            canManage={canManage} busy={decisionBusyId !== null}
            deciding={decisionBusyId === suggestion.id} onDecide={onDecide} />
        ))}
        {suggestions.length === 0 && <p className="quiet-empty">Lần xử lý này chưa tạo ra gợi ý nào.</p>}
      </div>
    </section>
  );
}

function SuggestionCard({ suggestion, index, canManage, busy, deciding, onDecide }) {
  const proposal = suggestion.proposed || {};
  return (
    <article className={`ai-suggestion ai-suggestion--${String(suggestion.decisionStatus).toLowerCase()}`}>
      <header><span>Gợi ý {index + 1}</span><strong>{decisionLabel(suggestion.decisionStatus)}</strong></header>
      <h3>{proposal.title || "Công việc con"}</h3>
      {proposal.description && <p>{proposal.description}</p>}
      <div className="ai-suggestion__meta"><span>Ước tính {formatMinutes(proposal.estimatedMinutes)}</span><span>{suggestion.type === "SUBTASK" ? "Công việc con" : suggestion.type}</span></div>
      {suggestion.createdTask && (
        <p className="ai-created-task"><strong>Đã tạo trên bảng:</strong> {suggestion.createdTask.title}</p>
      )}
      {suggestion.decisionStatus === "PENDING" && canManage && (
        <footer><button className="button button--secondary" type="button" disabled={busy}
          onClick={() => onDecide(suggestion, "reject")}>Từ chối</button>
          <button className="button button--primary" type="button" disabled={busy}
            onClick={() => onDecide(suggestion, "accept")}>{deciding ? "Đang xử lý…" : "Chấp nhận & tạo task"}</button></footer>
      )}
    </article>
  );
}

function AssistantState({ loading = false, text }) {
  return (
    <div className="ai-empty-state">
      {loading ? <span className="spinner" aria-hidden="true" /> : <span className="ai-empty-state__icon" aria-hidden="true">◇</span>}
      <strong>{text}</strong>
      {!loading && <p>Gợi ý chỉ trở thành công việc thật sau khi Chủ sở hữu chấp nhận.</p>}
    </div>
  );
}

function emptyText(tasks, sourceTask, boardLoading, canManage) {
  if (boardLoading) return "Đang tải dữ liệu dự án…";
  if (!tasks.length) return "Hãy tạo một công việc trước khi dùng Trợ lý.";
  if (!sourceTask) return "Chọn công việc cần chia nhỏ.";
  return canManage ? "Chưa có gợi ý cho công việc này. Hãy tạo gợi ý đầu tiên." : "Công việc này chưa có gợi ý để xem.";
}

function manageHint(project) {
  if (project.status !== "ACTIVE") return "Dự án đã đóng";
  if (project.currentUserRole !== "OWNER") return "Chỉ Chủ sở hữu có thể tạo gợi ý";
  return "Tạo từ 2 đến 6 gợi ý để Chủ sở hữu rà soát";
}

function aiErrorMessage(error, fallback) {
  if (error?.code === "PROJECT_OWNER_REQUIRED") return "Chỉ Chủ sở hữu dự án mới có quyền thực hiện thao tác này.";
  if (error?.code === "PROJECT_ARCHIVED") return "Dự án đã đóng và không thể thay đổi gợi ý.";
  if (error?.code === "AI_SUGGESTION_ALREADY_DECIDED") return "Gợi ý này đã được chấp nhận hoặc từ chối trước đó.";
  return error?.message || fallback;
}

function requestStatusLabel(status) {
  if (status === "SUCCEEDED") return "Đã tạo xong";
  if (status === "FAILED") return "Không thành công";
  return "Đang xử lý";
}

function decisionLabel(status) {
  if (status === "ACCEPTED") return "Đã chấp nhận";
  if (status === "REJECTED") return "Đã từ chối";
  return "Chờ duyệt";
}

function formatMinutes(value) {
  const minutes = Number(value);
  if (!Number.isFinite(minutes) || minutes <= 0) return "chưa xác định";
  if (minutes < 60) return `${minutes} phút`;
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  return remainder ? `${hours} giờ ${remainder} phút` : `${hours} giờ`;
}

function formatDateTime(value) {
  if (!value) return "chưa có thời gian";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "chưa có thời gian";
  return new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" }).format(parsed);
}
