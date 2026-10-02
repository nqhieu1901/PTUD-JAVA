import { useEffect, useRef, useState } from "react";
import { newPasswordError } from "../account-access.js";

function Dialog({ open, onClose, labelledBy, children }) {
  const ref = useRef(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog ref={ref} className="modal" aria-labelledby={labelledBy}
      onCancel={(event) => { event.preventDefault(); onClose(); }}
      onClick={(event) => event.target === event.currentTarget && onClose()}>
      {children}
    </dialog>
  );
}

export function ProjectDialog({ open, project, busy, error, onClose, onSubmit, onArchive, onRestore }) {
  const close = () => { if (!busy) onClose(); };
  const editing = Boolean(project);
  const archived = project?.status === "ARCHIVED";

  function submit(event) {
    event.preventDefault();
    if (archived) return;
    if (!event.currentTarget.reportValidity()) return;
    const data = Object.fromEntries(new FormData(event.currentTarget));
    onSubmit(buildProjectPayload(data));
  }

  function archive() {
    if (window.confirm(`Đóng dự án “${project.name}”? Sau đó dự án sẽ chuyển sang chế độ chỉ xem.`)) {
      onArchive(project);
    }
  }

  function restore() {
    if (window.confirm(`Mở lại dự án “${project.name}”? Dự án sẽ có thể chỉnh sửa và nhận công việc mới.`)) {
      onRestore(project);
    }
  }

  return (
    <Dialog open={open} onClose={close} labelledBy="project-dialog-title">
      <form className="modal__card" onSubmit={submit} noValidate>
        <header className="modal__header">
          <div><p className="eyebrow">{archived ? "Dự án đã đóng" : editing ? "Thông tin dự án" : "Không gian mới"}</p>
            <h2 id="project-dialog-title">{archived ? "Mở lại dự án" : editing ? "Chỉnh sửa dự án" : "Tạo dự án"}</h2></div>
          <button className="icon-button" type="button" aria-label="Đóng" disabled={busy} onClick={close}>×</button>
        </header>
        <div className="modal__body">
          {error && <div className="form-alert" role="alert">{error}</div>}
          {archived && (
            <div className="project-archived-notice" role="status">
              <strong>Dự án đang ở chế độ chỉ xem</strong>
              <span>Mở lại dự án để tiếp tục chỉnh sửa thông tin và quản lý công việc.</span>
            </div>
          )}
          <label className="field"><span>Tên dự án <em>*</em></span>
            <input name="name" maxLength="160" placeholder="Ví dụ: Website tốt nghiệp"
              defaultValue={project?.name || ""} required autoFocus disabled={archived} /></label>
          <label className="field"><span>Mô tả</span>
            <textarea name="description" rows="3" placeholder="Mục tiêu chính của dự án"
              defaultValue={project?.description || ""} disabled={archived} /></label>
          <div className="field-row">
            <label className="field"><span>Ngày bắt đầu</span>
              <input name="startDate" type="date" defaultValue={project?.startDate || ""} disabled={archived} /></label>
            <label className="field"><span>Hạn dự án</span>
              <input name="dueDate" type="date" defaultValue={project?.dueDate || ""} disabled={archived} /></label>
          </div>
        </div>
        <footer className={`modal__footer${editing && !archived ? " modal__footer--split" : ""}`}>
          {editing && !archived && <button className="button button--danger" type="button" disabled={busy} onClick={archive}>Đóng dự án</button>}
          <span className="modal__footer-actions">
            <button className="button button--ghost" type="button" disabled={busy} onClick={close}>{archived ? "Đóng" : "Hủy"}</button>
            {archived ? (
              <button className="button button--primary" type="button" disabled={busy} onClick={restore}>
                {busy ? "Đang mở lại…" : "Mở lại dự án"}
              </button>
            ) : (
              <button className="button button--primary" type="submit" disabled={busy}>
                {busy ? (editing ? "Đang lưu…" : "Đang tạo…") : (editing ? "Lưu thay đổi" : "Tạo dự án")}
              </button>
            )}
          </span>
        </footer>
      </form>
    </Dialog>
  );
}

export function AccountDialog({
  open, user, busyAction, profileError, passwordError, verificationError, verificationMessage,
  googleEnabled = false, googleError = "",
  onClose, onUpdateProfile, onChangePassword, onRequestVerification,
  onLinkGoogle = () => {}, onUnlinkGoogle = () => {}
}) {
  const [localPasswordError, setLocalPasswordError] = useState("");
  const busy = Boolean(busyAction);
  const close = () => { if (!busy) onClose(); };

  function submitProfile(event) {
    event.preventDefault();
    if (!event.currentTarget.reportValidity()) return;
    const data = Object.fromEntries(new FormData(event.currentTarget));
    onUpdateProfile({ fullName: data.fullName });
  }

  function submitPassword(event) {
    event.preventDefault();
    setLocalPasswordError("");
    if (!event.currentTarget.reportValidity()) return;
    const data = Object.fromEntries(new FormData(event.currentTarget));
    const validation = newPasswordError(data.newPassword, data.confirmPassword);
    if (validation) {
      setLocalPasswordError(validation);
      return;
    }
    onChangePassword({ currentPassword: data.currentPassword, newPassword: data.newPassword });
  }

  function submitGoogleConnection(event) {
    event.preventDefault();
    if (!event.currentTarget.reportValidity()) return;
    const data = Object.fromEntries(new FormData(event.currentTarget));
    if (user.googleLinked
      && !window.confirm("Hủy liên kết Google? Bạn sẽ cần đăng nhập lại bằng email và mật khẩu SmartTask.")) return;
    const payload = { currentPassword: data.googleCurrentPassword };
    if (user.googleLinked) onUnlinkGoogle(payload);
    else onLinkGoogle(payload);
  }

  return (
    <Dialog open={open} onClose={close} labelledBy="account-dialog-title">
      <div className="modal__card modal__card--wide account-dialog">
        <header className="modal__header">
          <div><p className="eyebrow">Tài khoản cá nhân</p><h2 id="account-dialog-title">Cài đặt tài khoản</h2></div>
          <button className="icon-button" type="button" aria-label="Đóng" disabled={busy} onClick={close}>×</button>
        </header>
        <div className="modal__body account-settings">
          <form className="account-section" onSubmit={submitProfile} noValidate>
            <div className="account-section__heading">
              <div><h3>Hồ sơ</h3><p>Cập nhật tên hiển thị trong dự án và công việc.</p></div>
              <button className="button button--primary button--compact" type="submit" disabled={busy}>
                {busyAction === "profile" ? "Đang lưu…" : "Lưu hồ sơ"}
              </button>
            </div>
            {profileError && <div className="form-alert" role="alert">{profileError}</div>}
            <div className="field-row">
              <label className="field"><span>Họ và tên <em>*</em></span>
                <input name="fullName" maxLength="150" defaultValue={user.fullName} required autoFocus /></label>
              <label className="field"><span>Email</span>
                <input value={user.email} readOnly aria-readonly="true" /></label>
            </div>
          </form>

          <section className="account-section" aria-labelledby="email-verification-heading">
            <div className="account-section__heading">
              <div><h3 id="email-verification-heading">Xác minh email</h3>
                <p>{user.emailVerified ? "Email đã được xác minh." : "Email chưa được xác minh. Bạn vẫn có thể sử dụng tài khoản."}</p></div>
              {!user.emailVerified && <button className="button button--secondary button--compact"
                type="button" disabled={busy} aria-busy={busyAction === "verification"}
                onClick={onRequestVerification}>
                {busyAction === "verification" ? "Đang gửi…" : "Gửi email xác minh"}
              </button>}
            </div>
            {verificationError && <div className="form-alert" role="alert">{verificationError}</div>}
            {verificationMessage && <div className="account-access-notice" role="status">{verificationMessage}</div>}
          </section>

          {googleEnabled && user.passwordLoginEnabled === true && (
            <form className="account-section" onSubmit={submitGoogleConnection} noValidate
              aria-labelledby="google-connection-heading">
              <div className="account-section__heading">
                <div><h3 id="google-connection-heading">Liên kết Google</h3>
                  <p>{user.googleLinked
                    ? "Google đang được liên kết. Bạn có thể đăng nhập bằng Google hoặc mật khẩu SmartTask."
                    : "Liên kết để đăng nhập nhanh bằng Google mà vẫn giữ mật khẩu SmartTask."}</p></div>
                <button className={`button button--compact ${user.googleLinked ? "button--danger" : "button--secondary"}`}
                  type="submit" disabled={busy}
                  aria-busy={busyAction === "google-link" || busyAction === "google-unlink"}>
                  {busyAction === "google-link" ? "Đang chuyển…"
                    : busyAction === "google-unlink" ? "Đang hủy…"
                      : user.googleLinked ? "Hủy liên kết" : "Liên kết Google"}
                </button>
              </div>
              {googleError && <div id="google-connection-error" className="form-alert" role="alert">{googleError}</div>}
              <label className="field"><span>Mật khẩu SmartTask hiện tại <em>*</em></span>
                <input name="googleCurrentPassword" type="password" maxLength="72"
                  autoComplete="current-password" required disabled={busy}
                  aria-describedby={`google-connection-hint${googleError ? " google-connection-error" : ""}`} /></label>
              <p id="google-connection-hint" className="account-access-hint">
                Nhập mật khẩu để xác nhận đúng là bạn trước khi thay đổi cách đăng nhập.
              </p>
            </form>
          )}

          {user.passwordLoginEnabled === false ? (
            <section className="account-section" aria-labelledby="google-account-heading">
              <div className="account-section__heading">
                <div><h3 id="google-account-heading">Đăng nhập bằng Google</h3>
                  <p>Tài khoản này sử dụng Google để đăng nhập và chưa có mật khẩu riêng tại SmartTask.
                    Bạn quản lý mật khẩu trong tài khoản Google của mình.</p></div>
              </div>
            </section>
          ) : <form className="account-section" onSubmit={submitPassword} noValidate>
            <div className="account-section__heading">
              <div><h3>Đổi mật khẩu</h3><p>Xác minh mật khẩu hiện tại trước khi đặt mật khẩu mới.
                Sau khi đổi, bạn cần đăng nhập lại trên tất cả thiết bị.</p></div>
              <button className="button button--secondary button--compact" type="submit" disabled={busy}
                aria-busy={busyAction === "password"}>
                {busyAction === "password" ? "Đang đổi…" : "Đổi mật khẩu"}
              </button>
            </div>
            {(passwordError || localPasswordError) && (
              <div className="form-alert" role="alert">{localPasswordError || passwordError}</div>
            )}
            <label className="field"><span>Mật khẩu hiện tại <em>*</em></span>
              <input name="currentPassword" type="password" maxLength="72" autoComplete="current-password" required /></label>
            <div className="field-row">
              <label className="field"><span>Mật khẩu mới <em>*</em></span>
                <input name="newPassword" type="password" minLength="8" maxLength="72"
                  autoComplete="new-password" required aria-describedby="account-password-hint" /></label>
              <label className="field"><span>Xác nhận mật khẩu mới <em>*</em></span>
                <input name="confirmPassword" type="password" minLength="8" maxLength="72"
                  autoComplete="new-password" required aria-describedby="account-password-hint" /></label>
            </div>
            <p id="account-password-hint" className="account-access-hint">Ít nhất 8 ký tự, tối đa 72 byte UTF-8.</p>
          </form>}
        </div>
        <footer className="modal__footer">
          <button className="button button--ghost" type="button" disabled={busy} onClick={close}>Đóng</button>
        </footer>
      </div>
    </Dialog>
  );
}

export function TaskDialog({
  open, task, tasks = [], members = [], statuses = [], busy, error, readOnly = false, user, project,
  comments = [], commentsLoading = false, commentsError = "", commentBusyAction = null,
  attachments = [], attachmentsLoading = false, attachmentsError = "", attachmentBusyAction = null,
  onClose, onSubmit, onDelete, onReloadComments = () => {},
  onAddComment = async () => false, onDeleteComment = () => {},
  onReloadAttachments = () => {}, onUploadAttachment = async () => false,
  onDownloadAttachment = () => {}, onDeleteAttachment = () => {}
}) {
  const [commentContent, setCommentContent] = useState("");
  const editing = Boolean(task);
  const viewOnly = editing && readOnly;
  const commentBusy = Boolean(commentBusyAction);
  const attachmentBusy = Boolean(attachmentBusyAction);
  const close = () => { if (!busy && !commentBusy && !attachmentBusy) onClose(); };
  const canComment = editing && project?.status === "ACTIVE"
    && ["OWNER", "MEMBER"].includes(project.currentUserRole);
  const initialStatusName = statuses.find((status) => !status.terminal)?.displayName || "Cần làm";

  useEffect(() => setCommentContent(""), [open, task?.id]);

  function submit(event) {
    event.preventDefault();
    if (viewOnly) return;
    if (!event.currentTarget.reportValidity()) return;
    const data = Object.fromEntries(new FormData(event.currentTarget));
    onSubmit(buildTaskPayload(data));
  }

  function remove() {
    if (viewOnly) return;
    if (window.confirm(`Xóa công việc “${task.title}”? Thao tác này không thể hoàn tác.`)) {
      onDelete(task);
    }
  }

  async function submitComment(event) {
    event.preventDefault();
    const content = commentContent.trim();
    if (!canComment || !content || commentBusy) return;
    if (await onAddComment(buildCommentPayload(content))) setCommentContent("");
  }

  function removeComment(comment) {
    if (!canDeleteComment(project, user, comment)) return;
    if (window.confirm(`Xóa bình luận của ${comment.author.fullName}?`)) onDeleteComment(comment);
  }

  return (
    <Dialog open={open} onClose={close} labelledBy="task-dialog-title">
      <div className="modal__card modal__card--wide task-dialog">
        <header className="modal__header">
          <div>
            <p className="eyebrow">{viewOnly ? "Chi tiết công việc" : editing ? "Cập nhật chi tiết" : `Thêm vào cột ${initialStatusName}`}</p>
            <h2 id="task-dialog-title">{viewOnly ? "Xem công việc" : editing ? "Sửa công việc" : "Tạo công việc"}</h2>
          </div>
          <button className="icon-button" type="button" aria-label="Đóng"
            disabled={busy || commentBusy || attachmentBusy} onClick={close}>×</button>
        </header>
        <div className="modal__body task-dialog__body">
          {error && <div className="form-alert" role="alert">{error}</div>}
          {viewOnly && project?.currentUserRole === "VIEWER" && project.status === "ACTIVE" && (
            <div className="board-notice" role="status">Bạn đang có quyền Người xem nên không thể chỉnh sửa hoặc bình luận.</div>
          )}
          <form id="task-details-form" className="task-details-form" onSubmit={submit} noValidate>
            <label className="field"><span>Tên công việc <em>*</em></span>
              <input name="title" maxLength="255" placeholder="Việc cần hoàn thành"
                defaultValue={task?.title || ""} required autoFocus={!viewOnly} disabled={viewOnly} /></label>
            <label className="field"><span>Mô tả</span>
              <textarea name="description" rows="3" maxLength="16000" placeholder="Thêm bối cảnh hoặc tiêu chí hoàn thành"
                defaultValue={task?.description || ""} disabled={viewOnly} /></label>
            <div className="field-row">
              <label className="field"><span>Ưu tiên</span>
                <select name="priority" defaultValue={task?.priority || "MEDIUM"} disabled={viewOnly}>
                  <option value="LOW">Thấp</option><option value="MEDIUM">Trung bình</option>
                  <option value="HIGH">Cao</option><option value="URGENT">Khẩn cấp</option>
                </select></label>
              <label className="field"><span>Người phụ trách</span>
                <select name="assigneeId" defaultValue={task?.assignee?.id || ""} disabled={viewOnly}>
                  <option value="">Chưa phân công</option>
                  {members.filter((member) => member.role !== "VIEWER").map((member) => (
                    <option key={member.userId} value={member.userId}>{member.fullName} · {member.email}</option>
                  ))}
                </select></label>
            </div>
            <label className="field"><span>Công việc cha</span>
              <select name="parentTaskId" defaultValue={task?.parentTaskId || ""} disabled={viewOnly}>
                <option value="">Không có — công việc độc lập</option>
                {tasks.filter((candidate) => candidate.id !== task?.id).map((candidate) => (
                  <option key={candidate.id} value={candidate.id}>{candidate.title}</option>
                ))}
              </select>
              <small>Dùng để chia một đầu việc lớn thành các công việc con.</small>
            </label>
            <div className="field-row">
              <label className="field"><span>Bắt đầu</span>
                <input name="startAt" type="datetime-local" defaultValue={toDateTimeLocal(task?.startAt)} disabled={viewOnly} /></label>
              <label className="field"><span>Deadline <em>*</em></span>
                <input name="dueAt" type="datetime-local" defaultValue={toDateTimeLocal(task?.dueAt) || defaultDueAt()}
                  required disabled={viewOnly} /></label>
            </div>
            <label className="field"><span>Thời lượng dự kiến (phút)</span>
              <input name="estimatedMinutes" type="number" min="1" max="100000" placeholder="Ví dụ: 90"
                defaultValue={task?.estimatedMinutes || ""} disabled={viewOnly} /></label>
          </form>
          {editing && (
            <>
              <TaskAttachments key={task.id} attachments={attachments} loading={attachmentsLoading} error={attachmentsError}
                project={project} user={user} busyAction={attachmentBusyAction}
                onUpload={onUploadAttachment} onDownload={onDownloadAttachment}
                onRetry={onReloadAttachments} onDelete={onDeleteAttachment} />
              <TaskComments comments={comments} loading={commentsLoading} error={commentsError}
                content={commentContent} canComment={canComment} project={project} user={user}
                busyAction={commentBusyAction} onContentChange={setCommentContent}
                onSubmit={submitComment} onRetry={onReloadComments} onDelete={removeComment} />
            </>
          )}
        </div>
        <footer className={`modal__footer${editing && !viewOnly ? " modal__footer--split" : ""}`}>
          {editing && !viewOnly && <button className="button button--danger" type="button"
            disabled={busy || commentBusy || attachmentBusy} onClick={remove}>Xóa công việc</button>}
          <span className="modal__footer-actions">
            <button className="button button--ghost" type="button" disabled={busy || commentBusy || attachmentBusy}
              onClick={close}>{viewOnly ? "Đóng" : "Hủy"}</button>
            {!viewOnly && (
              <button className="button button--primary" type="submit" form="task-details-form"
                disabled={busy || commentBusy || attachmentBusy}>
                {busy ? (editing ? "Đang lưu…" : "Đang tạo…") : (editing ? "Lưu thay đổi" : "Tạo công việc")}
              </button>
            )}
          </span>
        </footer>
      </div>
    </Dialog>
  );
}

function TaskAttachments({
  attachments, loading, error, project, user, busyAction, onUpload, onDownload, onRetry, onDelete
}) {
  const [selectedFile, setSelectedFile] = useState(null);
  const [selectionError, setSelectionError] = useState("");
  const canUpload = project?.status === "ACTIVE"
    && ["OWNER", "MEMBER"].includes(project.currentUserRole);

  function chooseFile(event) {
    const file = event.target.files?.[0] || null;
    if (file && file.size > 10 * 1024 * 1024) {
      setSelectedFile(null);
      setSelectionError("Tệp đính kèm không được vượt quá 10 MB.");
      event.target.value = "";
      return;
    }
    setSelectedFile(file);
    setSelectionError("");
  }

  async function submit(event) {
    event.preventDefault();
    if (!canUpload || !selectedFile || busyAction) return;
    const form = event.currentTarget;
    if (await onUpload(selectedFile)) {
      form.reset();
      setSelectedFile(null);
      setSelectionError("");
    }
  }

  function remove(attachment) {
    if (!canDeleteAttachment(project, user, attachment)) return;
    if (window.confirm(`Xóa tệp “${attachment.originalName}”?`)) onDelete(attachment);
  }

  return (
    <section className="task-attachments" aria-labelledby="task-attachments-title" aria-busy={loading}>
      <div className="task-attachments__heading">
        <div><h3 id="task-attachments-title">Tệp đính kèm</h3><p>Tài liệu, hình ảnh và tệp tham khảo của công việc.</p></div>
        <span className="count-badge" aria-label={`${attachments.length} tệp`}>{attachments.length}</span>
      </div>
      {(error || selectionError) && (
        <div className="attachment-error form-alert" role="alert">
          <span>{selectionError || error}</span>
          {error && <button className="button button--ghost button--compact" type="button"
            disabled={loading || Boolean(busyAction)} onClick={onRetry}>Thử lại</button>}
        </div>
      )}
      {loading ? (
        <div className="attachments-state" role="status"><span className="spinner" aria-hidden="true" /> Đang tải tệp…</div>
      ) : attachments.length ? (
        <div className="attachment-list" role="list">
          {attachments.map((attachment) => (
            <article className="attachment-row" role="listitem" key={attachment.id}>
              <span className="attachment-icon" aria-hidden="true">{attachmentIcon(attachment.originalName)}</span>
              <div className="attachment-row__content">
                <strong title={attachment.originalName}>{attachment.originalName}</strong>
                <small>{formatFileSize(attachment.sizeBytes)} · {attachment.uploadedBy?.fullName || "Người dùng"} · {formatCommentDate(attachment.createdAt)}</small>
              </div>
              <button className="button button--ghost button--compact" type="button"
                disabled={Boolean(busyAction)} onClick={() => onDownload(attachment)}>
                {busyAction === `download:${attachment.id}` ? "Đang tải…" : "Tải xuống"}
              </button>
              {canDeleteAttachment(project, user, attachment) && (
                <button className="icon-button attachment-delete" type="button"
                  aria-label={`Xóa tệp ${attachment.originalName}`} title="Xóa tệp"
                  disabled={Boolean(busyAction)} onClick={() => remove(attachment)}>
                  {busyAction === `delete:${attachment.id}` ? "…" : "×"}
                </button>
              )}
            </article>
          ))}
        </div>
      ) : !error && <div className="attachments-state">Chưa có tệp đính kèm.</div>}
      {canUpload ? (
        <form className="attachment-uploader" onSubmit={submit}>
          <label className="field" htmlFor="task-attachment-file"><span>Thêm tệp</span></label>
          <div className="attachment-uploader__row">
            <input id="task-attachment-file" type="file" disabled={Boolean(busyAction)}
              accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.md,.jpg,.jpeg,.png,.gif,.webp,.zip"
              onChange={chooseFile} />
            <button className="button button--secondary" type="submit"
              disabled={Boolean(busyAction) || !selectedFile}>
              {busyAction === "upload" ? "Đang tải lên…" : "Tải lên"}
            </button>
          </div>
          <small>Tối đa 10 MB · PDF, Office, văn bản, ảnh hoặc ZIP.</small>
        </form>
      ) : project?.status === "ARCHIVED" ? (
        <div className="attachments-readonly" role="status">Dự án đã đóng nên tệp chỉ có thể xem và tải xuống.</div>
      ) : project?.currentUserRole === "VIEWER" ? (
        <div className="attachments-readonly" role="status">Người xem có thể tải xuống nhưng không thể thêm hoặc xóa tệp.</div>
      ) : null}
    </section>
  );
}

function TaskComments({
  comments, loading, error, content, canComment, project, user, busyAction,
  onContentChange, onSubmit, onRetry, onDelete
}) {
  return (
    <section className="task-comments" aria-labelledby="task-comments-title" aria-busy={loading}>
      <div className="task-comments__heading">
        <div><h3 id="task-comments-title">Bình luận</h3><p>Trao đổi và lưu lại thông tin liên quan đến công việc.</p></div>
        <span className="count-badge" aria-label={`${comments.length} bình luận`}>{comments.length}</span>
      </div>
      {error && (
        <div className="comment-error form-alert" role="alert">
          <span>{error}</span>
          <button className="button button--ghost button--compact" type="button" disabled={loading || Boolean(busyAction)} onClick={onRetry}>Thử lại</button>
        </div>
      )}
      {loading ? (
        <div className="comments-state" role="status"><span className="spinner" aria-hidden="true" /> Đang tải bình luận…</div>
      ) : comments.length ? (
        <div className="comment-list" role="list">
          {comments.map((comment) => (
            <article className="comment-row" role="listitem" key={comment.id}>
              <span className="avatar" aria-hidden="true">{initials(comment.author?.fullName)}</span>
              <div className="comment-row__content">
                <div className="comment-row__meta"><strong>{comment.author?.fullName || "Người dùng"}</strong>
                  <time dateTime={comment.createdAt}>{formatCommentDate(comment.createdAt)}</time></div>
                <p>{comment.content}</p>
              </div>
              {canDeleteComment(project, user, comment) && (
                <button className="icon-button comment-delete" type="button"
                  aria-label={`Xóa bình luận của ${comment.author?.fullName || "người dùng"}`}
                  title="Xóa bình luận" disabled={Boolean(busyAction)} onClick={() => onDelete(comment)}>
                  {busyAction === `delete:${comment.id}` ? "…" : "×"}
                </button>
              )}
            </article>
          ))}
        </div>
      ) : !error && <div className="comments-state">Chưa có bình luận nào.</div>}
      {canComment ? (
        <form className="comment-composer" onSubmit={onSubmit}>
          <label className="field" htmlFor="task-comment-content"><span>Thêm bình luận</span></label>
          <div className="comment-composer__row">
            <textarea id="task-comment-content" rows="2" maxLength="4000" value={content}
              placeholder="Viết nội dung trao đổi…" disabled={Boolean(busyAction)} required
              onChange={(event) => onContentChange(event.target.value)} />
            <button className="button button--primary" type="submit"
              disabled={Boolean(busyAction) || !content.trim()}>{busyAction === "create" ? "Đang gửi…" : "Gửi"}</button>
          </div>
        </form>
      ) : project?.status === "ARCHIVED" ? (
        <div className="comments-readonly" role="status">Dự án đã đóng nên bình luận chỉ có thể xem.</div>
      ) : project?.currentUserRole === "VIEWER" ? (
        <div className="comments-readonly" role="status">Người xem có thể đọc nhưng không thể thêm hoặc xóa bình luận.</div>
      ) : null}
    </section>
  );
}

export function WorkflowDialog({ open, project, statuses, busy, error, onClose, onSubmit }) {
  const nextKey = useRef(0);
  const [items, setItems] = useState(() => statuses.map((status) => ({ ...status, localKey: `status-${status.id}` })));
  const [localError, setLocalError] = useState("");
  const close = () => { if (!busy) onClose(); };

  useEffect(() => {
    setItems(statuses.map((status) => ({ ...status, localKey: `status-${status.id}` })));
    setLocalError("");
  }, [open, statuses]);

  function updateItem(localKey, changes) {
    setLocalError("");
    setItems((current) => current.map((item) => item.localKey === localKey ? { ...item, ...changes } : item));
  }

  function chooseTerminal(localKey) {
    setLocalError("");
    setItems((current) => current.map((item) => ({ ...item, terminal: item.localKey === localKey })));
  }

  function move(localKey, offset) {
    setItems((current) => {
      const index = current.findIndex((item) => item.localKey === localKey);
      const target = index + offset;
      if (index < 0 || target < 0 || target >= current.length) return current;
      const next = [...current];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  }

  function addStatus() {
    if (items.length >= 7) return;
    const key = `new-${Date.now()}-${nextKey.current++}`;
    setItems((current) => [...current, {
      id: null, code: null, displayName: "Cột mới", terminal: false, localKey: key
    }]);
  }

  function removeStatus(localKey) {
    if (items.length <= 2) return;
    setItems((current) => {
      const removed = current.find((item) => item.localKey === localKey);
      const next = current.filter((item) => item.localKey !== localKey);
      if (removed?.terminal && next.length) {
        next[next.length - 1] = { ...next[next.length - 1], terminal: true };
      }
      return next;
    });
  }

  function submit(event) {
    event.preventDefault();
    if (!event.currentTarget.reportValidity()) return;
    const names = items.map((item) => item.displayName.trim().toLocaleLowerCase("vi"));
    if (new Set(names).size !== names.length) {
      setLocalError("Tên các trạng thái không được trùng nhau.");
      return;
    }
    if (items.filter((item) => item.terminal).length !== 1) {
      setLocalError("Hãy chọn đúng một trạng thái hoàn tất.");
      return;
    }
    onSubmit(buildWorkflowPayload(items));
  }

  return (
    <Dialog open={open} onClose={close} labelledBy="workflow-dialog-title">
      <form className="modal__card modal__card--wide workflow-dialog" onSubmit={submit} noValidate>
        <header className="modal__header">
          <div><p className="eyebrow">{project.name}</p><h2 id="workflow-dialog-title">Cột trạng thái Kanban</h2></div>
          <button className="icon-button" type="button" aria-label="Đóng" disabled={busy} onClick={close}>×</button>
        </header>
        <div className="modal__body">
          {(error || localError) && <div className="form-alert" role="alert">{localError || error}</div>}
          <p className="workflow-help">Thêm, đổi tên, xóa hoặc sắp xếp các cột từ trái sang phải. Kanban cần từ 2 đến 7 cột và đúng một cột hoàn tất.</p>
          <div className="workflow-list" role="list" aria-label={`${items.length} trạng thái`}>
            {items.map((item, index) => (
              <div className="workflow-row" role="listitem" key={item.localKey}>
                <span className="workflow-order" aria-hidden="true">{index + 1}</span>
                <label className="field workflow-name"><span>Tên trạng thái</span>
                  <input value={item.displayName} maxLength="100" required disabled={busy}
                    aria-label={`Tên trạng thái ${index + 1}`} onChange={(event) => updateItem(item.localKey, { displayName: event.target.value })} />
                </label>
                <label className="workflow-terminal">
                  <input type="radio" name="terminalStatus" checked={item.terminal} disabled={busy}
                    onChange={() => chooseTerminal(item.localKey)} /> Hoàn tất
                </label>
                <span className="workflow-row__actions">
                  <button className="icon-button icon-button--bordered" type="button" disabled={busy || index === 0}
                    aria-label={`Đưa ${item.displayName} sang trái`} onClick={() => move(item.localKey, -1)}>←</button>
                  <button className="icon-button icon-button--bordered" type="button" disabled={busy || index === items.length - 1}
                    aria-label={`Đưa ${item.displayName} sang phải`} onClick={() => move(item.localKey, 1)}>→</button>
                  <button className="icon-button workflow-remove" type="button" disabled={busy || items.length <= 2}
                    aria-label={`Xóa trạng thái ${item.displayName}`} onClick={() => removeStatus(item.localKey)}>×</button>
                </span>
              </div>
            ))}
          </div>
          <button className="button button--secondary button--compact workflow-add" type="button"
            disabled={busy || items.length >= 7} onClick={addStatus}>+ Thêm cột</button>
        </div>
        <footer className="modal__footer">
          <button className="button button--ghost" type="button" disabled={busy} onClick={close}>Hủy</button>
          <button className="button button--primary" type="submit" disabled={busy}>
            {busy ? "Đang lưu…" : "Lưu các cột"}
          </button>
        </footer>
      </form>
    </Dialog>
  );
}

export function MembersDialog({ open, project, members, busy, error, onClose, onAdd, onRemove, onChangeRole }) {
  const [email, setEmail] = useState("");
  const [role, setRole] = useState("MEMBER");
  const close = () => { if (!busy) onClose(); };
  const canManage = project.currentUserRole === "OWNER" && project.status === "ACTIVE";

  async function submit(event) {
    event.preventDefault();
    if (!event.currentTarget.reportValidity()) return;
    if (await onAdd(buildMemberPayload(email, role))) {
      setEmail("");
      setRole("MEMBER");
    }
  }

  function remove(member) {
    if (window.confirm(`Gỡ ${member.fullName} khỏi dự án?`)) onRemove(member);
  }

  return (
    <Dialog open={open} onClose={close} labelledBy="members-dialog-title">
      <div className="modal__card modal__card--wide">
        <header className="modal__header">
          <div><p className="eyebrow">{project.name}</p><h2 id="members-dialog-title">Thành viên dự án</h2></div>
          <button className="icon-button" type="button" aria-label="Đóng" disabled={busy} onClick={close}>×</button>
        </header>
        <div className="modal__body">
          {error && <div className="form-alert" role="alert">{error}</div>}
          {canManage && (
            <form className="member-add-form" onSubmit={submit} noValidate>
              <div className="member-add-row">
                <label className="field"><span>Thêm bằng email</span>
                  <input type="email" value={email} onChange={(event) => setEmail(event.target.value)}
                    maxLength="254" placeholder="thanhvien@example.com" required autoFocus /></label>
                <label className="field"><span>Vai trò</span>
                  <select value={role} onChange={(event) => setRole(event.target.value)}>
                    <option value="MEMBER">Thành viên</option><option value="VIEWER">Người xem</option>
                  </select></label>
                <button className="button button--primary" type="submit" disabled={busy}>Thêm</button>
              </div>
            </form>
          )}
          {!canManage && project.status === "ARCHIVED" && (
            <div className="board-notice" role="status">Dự án đã đóng. Danh sách thành viên chỉ có thể xem.</div>
          )}
          {!canManage && project.status === "ACTIVE" && project.currentUserRole === "VIEWER" && (
            <div className="board-notice" role="status">Bạn đang có quyền Người xem. Chỉ chủ sở hữu mới có thể quản lý thành viên.</div>
          )}
          <div className="member-list" role="list" aria-label={`${members.length} thành viên`}>
            {members.map((member) => (
              <article className="member-row" key={member.userId} role="listitem">
                <span className="avatar" aria-hidden="true">{initials(member.fullName)}</span>
                <span className="member-row__copy"><strong>{member.fullName}</strong><small>{member.email}</small></span>
                {canManage && member.role !== "OWNER" ? (
                  <label className="member-role-control"><span className="sr-only">Vai trò của {member.fullName}</span>
                    <select value={member.role} disabled={busy}
                      aria-label={`Vai trò của ${member.fullName}`}
                      onChange={(event) => onChangeRole(member, event.target.value)}>
                      <option value="MEMBER">Thành viên</option><option value="VIEWER">Người xem</option>
                    </select></label>
                ) : <span className="role-badge">{roleLabel(member.role)}</span>}
                {canManage && member.role !== "OWNER" && <button className="button button--ghost button--compact" type="button" disabled={busy}
                  onClick={() => remove(member)}>Gỡ</button>}
              </article>
            ))}
          </div>
        </div>
        <footer className="modal__footer">
          <button className="button button--secondary" type="button" disabled={busy} onClick={close}>Đóng</button>
        </footer>
      </div>
    </Dialog>
  );
}

export function buildTaskPayload(data) {
  return {
    title: data.title,
    description: data.description.trim() || null,
    priority: data.priority,
    startAt: data.startAt || null,
    dueAt: data.dueAt,
    estimatedMinutes: data.estimatedMinutes ? Number(data.estimatedMinutes) : null,
    assigneeId: data.assigneeId ? Number(data.assigneeId) : null,
    parentTaskId: data.parentTaskId ? Number(data.parentTaskId) : null
  };
}

export function buildProjectPayload(data) {
  return {
    name: data.name,
    description: data.description.trim() || null,
    startDate: data.startDate || null,
    dueDate: data.dueDate || null
  };
}

export function buildMemberPayload(email, role) {
  return { email: email.trim(), role };
}

export function buildCommentPayload(content) {
  return { content: content.trim() };
}

export function buildWorkflowPayload(items) {
  return items.map((item) => ({
    id: item.id ?? null,
    displayName: item.displayName.trim(),
    terminal: Boolean(item.terminal)
  }));
}

export function toDateTimeLocal(value) {
  return value ? String(value).slice(0, 16) : "";
}

export function roleLabel(role) {
  if (role === "OWNER") return "Chủ sở hữu";
  if (role === "VIEWER") return "Người xem";
  return "Thành viên";
}

export function canDeleteComment(project, user, comment) {
  return project?.status === "ACTIVE" && Boolean(user)
    && ["OWNER", "MEMBER"].includes(project.currentUserRole)
    && (project.currentUserRole === "OWNER" || comment?.author?.id === user.id);
}

export function canDeleteAttachment(project, user, attachment) {
  return project?.status === "ACTIVE" && Boolean(user)
    && ["OWNER", "MEMBER"].includes(project.currentUserRole)
    && (project.currentUserRole === "OWNER" || attachment?.uploadedBy?.id === user.id);
}

export function formatFileSize(value) {
  const bytes = Number(value);
  if (!Number.isFinite(bytes) || bytes < 0) return "Không rõ dung lượng";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(bytes < 10 * 1024 ? 1 : 0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatCommentDate(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Không rõ thời gian";
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit"
  }).format(date);
}

function attachmentIcon(name) {
  const extension = String(name || "").split(".").at(-1).toLocaleLowerCase("vi");
  if (["jpg", "jpeg", "png", "gif", "webp"].includes(extension)) return "▧";
  if (["xls", "xlsx", "csv"].includes(extension)) return "▦";
  if (["zip"].includes(extension)) return "◇";
  return "▤";
}

function defaultDueAt() {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  date.setHours(17, 0, 0, 0);
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function initials(name) {
  const parts = String(name || "").trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return "?";
  return (parts.length === 1 ? parts[0].slice(0, 2) : parts[0][0] + parts.at(-1)[0]).toLocaleUpperCase("vi");
}
