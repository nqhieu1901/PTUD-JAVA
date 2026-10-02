import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError, clearCsrf, downloadFile, refreshCsrf } from "./api.js";
import { moveTaskLocally } from "./board.js";
import {
  markAllNotificationsRead, markNotificationRead, NOTIFICATION_REFRESH_INTERVAL_MS,
  shouldAutoRefreshNotifications
} from "./notifications.js";
import AuthView from "./components/AuthView.jsx";
import EmailActionView from "./components/EmailActionView.jsx";
import { accountAccessError, consumeEmailAction } from "./account-access.js";
import {
  AccountDialog, MembersDialog, ProjectDialog, TaskDialog, WorkflowDialog,
  canDeleteAttachment, canDeleteComment, roleLabel
} from "./components/Dialogs.jsx";
import Workspace from "./components/Workspace.jsx";

export default function App() {
  const initialized = useRef(false);
  const sessionVersion = useRef(0);
  const projectRequest = useRef(0);
  const boardRequest = useRef(0);
  const notificationRequest = useRef(0);
  const commentRequest = useRef(0);
  const attachmentRequest = useRef(0);
  const dialogIntent = useRef(0);
  const activeProjectId = useRef(null);
  const currentUserRef = useRef(null);
  const emailActionSequence = useRef(0);
  const googleAccountRequest = useRef(false);
  const [emailAction, setEmailAction] = useState(null);
  const [phase, setPhase] = useState("boot");
  const [user, setUser] = useState(null);
  const [projects, setProjects] = useState([]);
  const [project, setProject] = useState(null);
  const [tasks, setTasks] = useState([]);
  const [members, setMembers] = useState([]);
  const [workflowStatuses, setWorkflowStatuses] = useState([]);
  const [filters, setFilters] = useState({ query: "", priority: "" });
  const [boardLoading, setBoardLoading] = useState(false);
  const [boardError, setBoardError] = useState("");
  const [authError, setAuthError] = useState("");
  const [authBusy, setAuthBusy] = useState(false);
  const [authProviders, setAuthProviders] = useState({ google: false });
  const [dialog, setDialog] = useState(null);
  const [editingTask, setEditingTask] = useState(null);
  const [editingProject, setEditingProject] = useState(null);
  const [dialogBusy, setDialogBusy] = useState(false);
  const [dialogError, setDialogError] = useState("");
  const [accountBusyAction, setAccountBusyAction] = useState(null);
  const [accountErrors, setAccountErrors] = useState({ profile: "", password: "", verification: "", google: "" });
  const [verificationMessage, setVerificationMessage] = useState("");
  const [taskComments, setTaskComments] = useState([]);
  const [commentsLoading, setCommentsLoading] = useState(false);
  const [commentsError, setCommentsError] = useState("");
  const [commentBusyAction, setCommentBusyAction] = useState(null);
  const [taskAttachments, setTaskAttachments] = useState([]);
  const [attachmentsLoading, setAttachmentsLoading] = useState(false);
  const [attachmentsError, setAttachmentsError] = useState("");
  const [attachmentBusyAction, setAttachmentBusyAction] = useState(null);
  const [movingIds, setMovingIds] = useState(new Set());
  const [notifications, setNotifications] = useState([]);
  const [notificationsLoading, setNotificationsLoading] = useState(false);
  const [notificationsError, setNotificationsError] = useState("");
  const [markingNotificationIds, setMarkingNotificationIds] = useState(new Set());
  const [markingAllNotifications, setMarkingAllNotifications] = useState(false);
  const [toasts, setToasts] = useState([]);

  const addToast = useCallback((title, detail = "", error = false) => {
    const id = `${Date.now()}-${Math.random()}`;
    setToasts((current) => [...current, { id, title, detail, error }]);
    window.setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), 4200);
  }, []);

  const expireSession = useCallback(() => {
    sessionVersion.current += 1;
    projectRequest.current += 1;
    boardRequest.current += 1;
    notificationRequest.current += 1;
    commentRequest.current += 1;
    attachmentRequest.current += 1;
    dialogIntent.current += 1;
    activeProjectId.current = null;
    currentUserRef.current = null;
    clearCsrf();
    setUser(null);
    setProjects([]);
    setProject(null);
    setTasks([]);
    setMembers([]);
    setWorkflowStatuses([]);
    setFilters({ query: "", priority: "" });
    setBoardLoading(false);
    setBoardError("");
    setMovingIds(new Set());
    setNotifications([]);
    setNotificationsLoading(false);
    setNotificationsError("");
    setMarkingNotificationIds(new Set());
    setMarkingAllNotifications(false);
    setDialog(null);
    setEditingTask(null);
    setEditingProject(null);
    setDialogBusy(false);
    setDialogError("");
    setAccountBusyAction(null);
    googleAccountRequest.current = false;
    setAccountErrors({ profile: "", password: "", verification: "", google: "" });
    setVerificationMessage("");
    setTaskComments([]);
    setCommentsLoading(false);
    setCommentsError("");
    setCommentBusyAction(null);
    setTaskAttachments([]);
    setAttachmentsLoading(false);
    setAttachmentsError("");
    setAttachmentBusyAction(null);
    setToasts([]);
    setPhase("auth");
    setAuthBusy(false);
    setAuthError("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
  }, []);

  const loadNotifications = useCallback(async () => {
    const requestId = ++notificationRequest.current;
    setNotificationsLoading(true);
    setNotificationsError("");
    try {
      const nextNotifications = await api("/api/notifications?unreadOnly=false");
      if (requestId !== notificationRequest.current) return false;
      setNotifications(Array.isArray(nextNotifications) ? nextNotifications : []);
      return true;
    } catch (error) {
      if (requestId !== notificationRequest.current) return false;
      if (error.status === 401) {
        expireSession();
        return false;
      }
      setNotificationsError(messageFor(error, "Không thể tải thông báo."));
      return true;
    } finally {
      if (requestId === notificationRequest.current) setNotificationsLoading(false);
    }
  }, [expireSession]);

  const loadTaskComments = useCallback(async (projectId, taskId) => {
    const requestId = ++commentRequest.current;
    setTaskComments([]);
    setCommentsLoading(true);
    setCommentsError("");
    try {
      const nextComments = await api(`/api/projects/${projectId}/tasks/${taskId}/comments`);
      if (requestId !== commentRequest.current) return false;
      setTaskComments(Array.isArray(nextComments) ? nextComments : []);
      return true;
    } catch (error) {
      if (requestId !== commentRequest.current) return false;
      if (error.status === 401) expireSession();
      else setCommentsError(messageFor(error, "Không thể tải bình luận."));
      return false;
    } finally {
      if (requestId === commentRequest.current) setCommentsLoading(false);
    }
  }, [expireSession]);

  const loadTaskAttachments = useCallback(async (projectId, taskId) => {
    const requestId = ++attachmentRequest.current;
    setTaskAttachments([]);
    setAttachmentsLoading(true);
    setAttachmentsError("");
    try {
      const nextAttachments = await api(`/api/projects/${projectId}/tasks/${taskId}/attachments`);
      if (requestId !== attachmentRequest.current) return false;
      setTaskAttachments(Array.isArray(nextAttachments) ? nextAttachments : []);
      return true;
    } catch (error) {
      if (requestId !== attachmentRequest.current) return false;
      if (error.status === 401) expireSession();
      else setAttachmentsError(messageFor(error, "Không thể tải tệp đính kèm."));
      return false;
    } finally {
      if (requestId === attachmentRequest.current) setAttachmentsLoading(false);
    }
  }, [expireSession]);

  const loadBoard = useCallback(async (selectedProject) => {
    if (!selectedProject) return null;
    const requestId = ++boardRequest.current;
    activeProjectId.current = selectedProject.id;
    setProject(selectedProject);
    setTasks([]);
    setMembers([]);
    setWorkflowStatuses([]);
    setBoardLoading(true);
    setBoardError("");
    try {
      const [nextTasks, nextMembers, nextWorkflowStatuses] = await Promise.all([
        api(`/api/projects/${selectedProject.id}/tasks`),
        api(`/api/projects/${selectedProject.id}/members`),
        api(`/api/projects/${selectedProject.id}/workflow`)
      ]);
      if (requestId !== boardRequest.current) return null;
      const latestUser = currentUserRef.current;
      const normalizedTasks = latestUser ? replaceUserInTasks(nextTasks, latestUser) : nextTasks;
      const normalizedMembers = latestUser ? replaceUserInMembers(nextMembers, latestUser) : nextMembers;
      setTasks(normalizedTasks);
      setMembers(normalizedMembers);
      setWorkflowStatuses(Array.isArray(nextWorkflowStatuses) ? nextWorkflowStatuses : []);
      return normalizedTasks;
    } catch (error) {
      if (requestId !== boardRequest.current) return null;
      if (error.status === 401) expireSession();
      else setBoardError(messageFor(error));
      return null;
    } finally {
      if (requestId === boardRequest.current) setBoardLoading(false);
    }
  }, [expireSession]);

  const refreshProjectList = useCallback(async () => {
    const version = sessionVersion.current;
    const requestId = ++projectRequest.current;
    let loadedProjects;
    try {
      loadedProjects = await api("/api/projects");
    } catch (error) {
      if (version !== sessionVersion.current || requestId !== projectRequest.current) return null;
      throw error;
    }
    if (version !== sessionVersion.current || requestId !== projectRequest.current) return null;
    const nextProjects = currentUserRef.current
      ? replaceUserInProjects(loadedProjects, currentUserRef.current)
      : loadedProjects;
    setProjects(nextProjects);
    return nextProjects;
  }, []);

  const loadProjects = useCallback(async (preferredProjectId, reloadBoard = true) => {
    const boardVersion = boardRequest.current;
    const nextProjects = await refreshProjectList();
    if (!nextProjects || boardVersion !== boardRequest.current) return;
    if (!nextProjects.length) {
      boardRequest.current += 1;
      dialogIntent.current += 1;
      setDialog(null);
      activeProjectId.current = null;
      setProject(null);
      setTasks([]);
      setMembers([]);
      setWorkflowStatuses([]);
      setBoardLoading(false);
      return;
    }
    const savedId = preferredProjectId || activeProjectId.current || readSavedProjectId();
    const selected = nextProjects.find((item) => item.id === Number(savedId)) || nextProjects[0];
    if (!reloadBoard && activeProjectId.current === selected.id) {
      setProject(selected);
      return;
    }
    if (activeProjectId.current && activeProjectId.current !== selected.id) {
      dialogIntent.current += 1;
      setDialog(null);
    }
    writeSavedProjectId(selected.id);
    await loadBoard(selected);
  }, [loadBoard, refreshProjectList]);

  const refreshWorkspace = useCallback(async (reloadBoard = false) => {
    const version = sessionVersion.current;
    await Promise.all([
      loadNotifications(),
      loadProjects(activeProjectId.current, reloadBoard).catch((error) => {
        if (version !== sessionVersion.current) return;
        if (error.status === 401) expireSession();
        else setNotificationsError(messageFor(error, "Không thể cập nhật danh sách dự án."));
      })
    ]);
  }, [loadNotifications, loadProjects, expireSession]);

  useEffect(() => {
    if (initialized.current) return;
    initialized.current = true;
    const initialEmailAction = consumeEmailAction();
    if (initialEmailAction) setEmailAction({ ...initialEmailAction, id: ++emailActionSequence.current });
    (async () => {
      const version = sessionVersion.current;
      const redirectError = consumeAuthRedirectError();
      const googleLinked = consumeGoogleLinkedSuccess();
      try {
        const [, providers] = await Promise.all([
          refreshCsrf(),
          api("/api/auth/providers")
        ]);
        setAuthProviders(providers || { google: false });
        const currentUser = await api("/api/auth/me");
        if (version !== sessionVersion.current) return;
        setUser(currentUser);
        currentUserRef.current = currentUser;
        if (shouldLoadUserWorkspace(currentUser)) {
          await loadProjects();
          if (version !== sessionVersion.current) return;
          if (!(await loadNotifications())) return;
        }
        if (version !== sessionVersion.current) return;
        setPhase("app");
        if (redirectError) {
          addToast("Không thể liên kết Google", redirectError, true);
        } else if (googleLinked && currentUser.googleLinked === true) {
          addToast("Đã liên kết Google", "Bạn có thể dùng Google hoặc mật khẩu SmartTask để đăng nhập.");
        }
      } catch (error) {
        if (version !== sessionVersion.current) return;
        setUser(null);
        currentUserRef.current = null;
        setPhase("auth");
        if (redirectError) {
          setAuthError(redirectError);
        } else if (!(error instanceof ApiError) || error.status !== 401) {
          setAuthError("Không thể kết nối máy chủ. Hãy kiểm tra backend rồi tải lại trang.");
        }
      }
    })();
  }, [loadNotifications, loadProjects]);

  useEffect(() => {
    function handleEmailLink() {
      const action = consumeEmailAction();
      if (!action) return;
      dialogIntent.current += 1;
      setDialog(null);
      setEmailAction({ ...action, id: ++emailActionSequence.current });
    }
    window.addEventListener("hashchange", handleEmailLink);
    return () => window.removeEventListener("hashchange", handleEmailLink);
  }, []);

  useEffect(() => {
    if (phase !== "app" || !user) return undefined;

    function refreshWhenVisible() {
      if (shouldAutoRefreshNotifications(phase, user, document.visibilityState)) {
        void refreshWorkspace();
      }
    }

    const intervalId = window.setInterval(refreshWhenVisible, NOTIFICATION_REFRESH_INTERVAL_MS);
    document.addEventListener("visibilitychange", refreshWhenVisible);
    return () => {
      window.clearInterval(intervalId);
      document.removeEventListener("visibilitychange", refreshWhenVisible);
    };
  }, [phase, user, refreshWorkspace]);

  async function authenticate(mode, data) {
    const version = ++sessionVersion.current;
    setAuthBusy(true);
    setAuthError("");
    try {
      if (mode === "register") {
        await api("/api/auth/register", { method: "POST", body: data });
      }
      const currentUser = await api("/api/auth/login", {
        method: "POST",
        body: { email: data.email, password: data.password }
      });
      if (version !== sessionVersion.current) return;
      clearCsrf();
      await refreshCsrf();
      if (version !== sessionVersion.current) return;
      setUser(currentUser);
      currentUserRef.current = currentUser;
      if (shouldLoadUserWorkspace(currentUser)) {
        await loadProjects();
        if (version !== sessionVersion.current) return;
        if (!(await loadNotifications())) return;
      }
      if (version !== sessionVersion.current) return;
      setPhase("app");
      if (mode === "register") addToast("Tài khoản đã sẵn sàng", "Hãy tạo dự án đầu tiên của bạn.");
    } catch (error) {
      if (version !== sessionVersion.current) return;
      setUser(null);
      currentUserRef.current = null;
      setAuthError(messageFor(error, mode === "register" ? "Không thể tạo tài khoản." : "Đăng nhập không thành công."));
    } finally {
      if (version === sessionVersion.current) setAuthBusy(false);
    }
  }

  function loginWithGoogle() {
    if (!authProviders.google || authBusy) return;
    setAuthError("");
    window.location.assign("/oauth2/authorization/google");
  }

  async function completeEmailAction() {
    const version = sessionVersion.current;
    setEmailAction((current) => current ? { ...current, token: undefined } : current);
    try {
      const currentUser = await api("/api/auth/me");
      if (version !== sessionVersion.current) return;
      currentUserRef.current = currentUser;
      setUser(currentUser);
    } catch (error) {
      if (version !== sessionVersion.current) return;
      if (error.status === 401) {
        expireSession();
        setAuthError("");
      } else {
        addToast("Chưa cập nhật được trạng thái tài khoản", "Thao tác đã thành công. Hãy tải lại trang để cập nhật trạng thái.", true);
      }
    }
  }

  async function logout() {
    const version = sessionVersion.current;
    try {
      await api("/api/auth/logout", { method: "POST" });
    } catch (error) {
      if (version !== sessionVersion.current) return;
      if (error.status !== 401) {
        addToast("Đăng xuất chưa thành công", messageFor(error), true);
        return;
      }
    }
    if (version !== sessionVersion.current) return;
    sessionVersion.current += 1;
    projectRequest.current += 1;
    boardRequest.current += 1;
    notificationRequest.current += 1;
    commentRequest.current += 1;
    attachmentRequest.current += 1;
    dialogIntent.current += 1;
    activeProjectId.current = null;
    currentUserRef.current = null;
    clearCsrf();
    setUser(null);
    setProjects([]);
    setProject(null);
    setTasks([]);
    setMembers([]);
    setWorkflowStatuses([]);
    setFilters({ query: "", priority: "" });
    setBoardLoading(false);
    setBoardError("");
    setMovingIds(new Set());
    setNotifications([]);
    setNotificationsLoading(false);
    setNotificationsError("");
    setMarkingNotificationIds(new Set());
    setMarkingAllNotifications(false);
    setDialog(null);
    setEditingTask(null);
    setEditingProject(null);
    setDialogBusy(false);
    setDialogError("");
    setAccountBusyAction(null);
    googleAccountRequest.current = false;
    setAccountErrors({ profile: "", password: "", verification: "", google: "" });
    setVerificationMessage("");
    setTaskComments([]);
    setCommentsLoading(false);
    setCommentsError("");
    setCommentBusyAction(null);
    setTaskAttachments([]);
    setAttachmentsLoading(false);
    setAttachmentsError("");
    setAttachmentBusyAction(null);
    setToasts([]);
    setPhase("auth");
    setAuthError("");
    try {
      await refreshCsrf();
    } catch {
      setAuthError("Không thể kết nối máy chủ. Hãy kiểm tra backend rồi tải lại trang.");
    }
  }

  async function selectProject(projectId) {
    const selected = projects.find((item) => item.id === projectId);
    if (!selected) return null;
    closeDialog();
    writeSavedProjectId(selected.id);
    setFilters({ query: "", priority: "" });
    return loadBoard(selected);
  }

  async function updateAccountProfile(payload) {
    if (accountBusyAction) return;
    setAccountBusyAction("profile");
    setAccountErrors((current) => ({ ...current, profile: "" }));
    try {
      const updatedUser = await api("/api/users/me", { method: "PUT", body: payload });
      currentUserRef.current = updatedUser;
      setUser(updatedUser);
      setProjects((current) => replaceUserInProjects(current, updatedUser));
      setProject((current) => current ? replaceUserInProjects([current], updatedUser)[0] : current);
      setTasks((current) => replaceUserInTasks(current, updatedUser));
      setMembers((current) => replaceUserInMembers(current, updatedUser));
      setDialog(null);
      addToast("Đã cập nhật hồ sơ", updatedUser.fullName);
    } catch (error) {
      if (error.status === 401) expireSession();
      else setAccountErrors((current) => ({
        ...current,
        profile: error.fieldErrors?.fullName || messageFor(error, "Không thể cập nhật hồ sơ.")
      }));
    } finally {
      setAccountBusyAction(null);
    }
  }

  async function changeAccountPassword(payload) {
    if (accountBusyAction) return;
    const version = sessionVersion.current;
    setAccountBusyAction("password");
    setAccountErrors((current) => ({ ...current, password: "" }));
    try {
      await api("/api/users/me/password", { method: "PUT", body: payload });
      if (version !== sessionVersion.current) return;
      expireSession();
      setAuthError("");
      addToast("Đã đổi mật khẩu", "Vui lòng đăng nhập lại bằng mật khẩu mới.");
    } catch (error) {
      if (version !== sessionVersion.current) return;
      if (error.status === 401) expireSession();
      else setAccountErrors((current) => ({
        ...current,
        password: error.fieldErrors?.currentPassword || error.fieldErrors?.newPassword
          || messageFor(error, "Không thể đổi mật khẩu.")
      }));
    } finally {
      if (version === sessionVersion.current) setAccountBusyAction(null);
    }
  }

  async function requestEmailVerification() {
    if (accountBusyAction || user?.emailVerified) return;
    const version = sessionVersion.current;
    const intent = dialogIntent.current;
    setAccountBusyAction("verification");
    setAccountErrors((current) => ({ ...current, verification: "" }));
    setVerificationMessage("");
    try {
      await api("/api/auth/email-verification/request", { method: "POST" });
      if (version !== sessionVersion.current || intent !== dialogIntent.current) return;
      setVerificationMessage("Hướng dẫn xác minh đã được gửi. Hãy kiểm tra hộp thư và thư rác của bạn.");
    } catch (error) {
      if (version !== sessionVersion.current || intent !== dialogIntent.current) return;
      if (error.status === 401) expireSession();
      else setAccountErrors((current) => ({ ...current, verification: accountAccessError(error) }));
    } finally {
      if (version === sessionVersion.current) setAccountBusyAction(null);
    }
  }

  async function linkGoogleAccount(payload) {
    if (googleAccountRequest.current || accountBusyAction || !authProviders.google
      || user?.passwordLoginEnabled !== true || user?.googleLinked) return;
    googleAccountRequest.current = true;
    const version = sessionVersion.current;
    const intent = dialogIntent.current;
    setAccountBusyAction("google-link");
    setAccountErrors((current) => ({ ...current, google: "" }));
    try {
      const result = await api("/api/users/me/google-link", { method: "POST", body: payload });
      if (version !== sessionVersion.current || intent !== dialogIntent.current) return;
      const authorizationPath = safeGoogleAuthorizationPath(result?.authorizationUrl);
      if (!authorizationPath) {
        setAccountErrors((current) => ({ ...current, google: "Không thể bắt đầu liên kết Google. Vui lòng thử lại." }));
        return;
      }
      window.location.assign(authorizationPath);
    } catch (error) {
      if (version !== sessionVersion.current || intent !== dialogIntent.current) return;
      if (error.status === 401) expireSession();
      else setAccountErrors((current) => ({ ...current, google: googleAccountError(error) }));
    } finally {
      googleAccountRequest.current = false;
      if (version === sessionVersion.current) setAccountBusyAction(null);
    }
  }

  async function unlinkGoogleAccount(payload) {
    if (googleAccountRequest.current || accountBusyAction || !authProviders.google
      || user?.passwordLoginEnabled !== true || !user?.googleLinked) return;
    googleAccountRequest.current = true;
    const version = sessionVersion.current;
    const intent = dialogIntent.current;
    setAccountBusyAction("google-unlink");
    setAccountErrors((current) => ({ ...current, google: "" }));
    try {
      await api("/api/users/me/google-unlink", { method: "POST", body: payload });
      if (version !== sessionVersion.current || intent !== dialogIntent.current) return;
      expireSession();
      setAuthError("");
      addToast("Đã hủy liên kết Google", "Vui lòng đăng nhập lại bằng email và mật khẩu SmartTask.");
    } catch (error) {
      if (version !== sessionVersion.current || intent !== dialogIntent.current) return;
      if (error.status === 401) expireSession();
      else setAccountErrors((current) => ({ ...current, google: googleAccountError(error) }));
    } finally {
      googleAccountRequest.current = false;
      if (version === sessionVersion.current) setAccountBusyAction(null);
    }
  }

  async function createProject(payload) {
    setDialogBusy(true);
    setDialogError("");
    try {
      const created = await api("/api/projects", { method: "POST", body: payload });
      setDialog(null);
      setEditingTask(null);
      setEditingProject(null);
      await loadProjects(created.id);
      addToast("Đã tạo dự án", created.name);
    } catch (error) {
      if (error.status === 401) expireSession();
      else setDialogError(messageFor(error, "Không thể tạo dự án."));
    } finally {
      setDialogBusy(false);
    }
  }

  async function updateProject(payload) {
    if (!editingProject || editingProject.status !== "ACTIVE" || editingProject.currentUserRole !== "OWNER") return;
    const projectId = editingProject.id;
    setDialogBusy(true);
    setDialogError("");
    try {
      const updated = await api(`/api/projects/${projectId}`, { method: "PUT", body: payload });
      if (activeProjectId.current === projectId) {
        setDialog(null);
        setEditingProject(null);
        await loadProjects(projectId);
        addToast("Đã cập nhật dự án", updated.name);
      }
    } catch (error) {
      if (error.status === 401) expireSession();
      else setDialogError(messageFor(error, "Không thể cập nhật dự án."));
    } finally {
      setDialogBusy(false);
    }
  }

  async function archiveProject(targetProject) {
    if (!editingProject || targetProject.id !== editingProject.id
      || editingProject.status !== "ACTIVE" || editingProject.currentUserRole !== "OWNER") return;
    const projectId = editingProject.id;
    setDialogBusy(true);
    setDialogError("");
    try {
      const archived = await api(`/api/projects/${projectId}/archive`, { method: "POST" });
      if (activeProjectId.current === projectId) {
        setDialog(null);
        setEditingProject(null);
        await loadProjects(projectId);
        addToast("Đã đóng dự án", archived.name);
      }
    } catch (error) {
      if (error.status === 401) expireSession();
      else setDialogError(messageFor(error, "Không thể đóng dự án."));
    } finally {
      setDialogBusy(false);
    }
  }

  async function restoreProject(targetProject) {
    if (!editingProject || targetProject.id !== editingProject.id
      || editingProject.status !== "ARCHIVED" || editingProject.currentUserRole !== "OWNER") return;
    const projectId = editingProject.id;
    setDialogBusy(true);
    setDialogError("");
    let restored;
    try {
      restored = await api(`/api/projects/${projectId}/restore`, { method: "POST" });
    } catch (error) {
      if (error.status === 401) expireSession();
      else setDialogError(messageFor(error, "Không thể mở lại dự án."));
      setDialogBusy(false);
      return;
    }

    const isActiveProject = activeProjectId.current === projectId;
    setProjects((current) => replaceProjectResponse(current, restored));
    if (isActiveProject) {
      setProject(restored);
      setBoardError("");
    }
    setDialog(null);
    setEditingProject(null);
    setDialogBusy(false);
    addToast("Đã mở lại dự án", restored.name);

    if (!isActiveProject) return;
    try {
      await loadProjects(projectId);
    } catch (error) {
      if (error.status === 401) expireSession();
      else if (activeProjectId.current === projectId) {
        setBoardError(messageFor(error, "Dự án đã được mở lại nhưng chưa thể tải dữ liệu mới."));
      }
    }
  }

  async function createTask(payload) {
    if (!project || boardLoading || project.status !== "ACTIVE" || project.currentUserRole !== "OWNER") return;
    const taskProject = project;
    const projectId = project.id;
    setDialogBusy(true);
    setDialogError("");
    try {
      const created = await api(`/api/projects/${projectId}/tasks`, {
        method: "POST",
        body: payload
      });
      if (activeProjectId.current === projectId) {
        setDialog(null);
        setEditingTask(null);
        await loadBoard(taskProject);
        addToast("Đã tạo công việc", created.title);
      }
    } catch (error) {
      if (error.status === 401) expireSession();
      else setDialogError(messageFor(error, "Không thể tạo công việc."));
    } finally {
      setDialogBusy(false);
    }
  }

  async function updateTask(payload) {
    if (!project || !editingTask || project.status !== "ACTIVE" || project.currentUserRole !== "OWNER") return;
    const taskProject = project;
    const projectId = project.id;
    const taskId = editingTask.id;
    setDialogBusy(true);
    setDialogError("");
    try {
      const updated = await api(`/api/projects/${projectId}/tasks/${taskId}`, { method: "PUT", body: payload });
      if (activeProjectId.current === projectId) {
        commentRequest.current += 1;
        attachmentRequest.current += 1;
        setTaskComments([]);
        setCommentsLoading(false);
        setCommentsError("");
        setCommentBusyAction(null);
        setTaskAttachments([]);
        setAttachmentsLoading(false);
        setAttachmentsError("");
        setAttachmentBusyAction(null);
        setDialog(null);
        setEditingTask(null);
        await loadBoard(taskProject);
        addToast("Đã cập nhật công việc", updated.title);
      }
    } catch (error) {
      if (error.status === 401) expireSession();
      else setDialogError(messageFor(error, "Không thể cập nhật công việc."));
    } finally {
      setDialogBusy(false);
    }
  }

  async function deleteTask(task) {
    if (!project || !editingTask || task.id !== editingTask.id
      || project.status !== "ACTIVE" || project.currentUserRole !== "OWNER") return;
    const taskProject = project;
    const projectId = project.id;
    setDialogBusy(true);
    setDialogError("");
    try {
      await api(`/api/projects/${projectId}/tasks/${task.id}`, { method: "DELETE" });
      if (activeProjectId.current === projectId) {
        commentRequest.current += 1;
        attachmentRequest.current += 1;
        setTaskComments([]);
        setCommentsLoading(false);
        setCommentsError("");
        setCommentBusyAction(null);
        setTaskAttachments([]);
        setAttachmentsLoading(false);
        setAttachmentsError("");
        setAttachmentBusyAction(null);
        setDialog(null);
        setEditingTask(null);
        await loadBoard(taskProject);
        addToast("Đã xóa công việc", task.title);
      }
    } catch (error) {
      if (error.status === 401) expireSession();
      else setDialogError(messageFor(error, "Không thể xóa công việc."));
    } finally {
      setDialogBusy(false);
    }
  }

  async function addMember(payload) {
    if (!project || project.status !== "ACTIVE" || project.currentUserRole !== "OWNER") return false;
    const projectId = project.id;
    setDialogBusy(true);
    setDialogError("");
    try {
      const added = await api(`/api/projects/${projectId}/members`, { method: "POST", body: payload });
      if (activeProjectId.current === projectId) {
        setMembers((current) => current.some((member) => member.userId === added.userId) ? current : [...current, added]);
        addToast("Đã thêm thành viên", added.fullName);
        return true;
      }
      return false;
    } catch (error) {
      if (error.status === 401) expireSession();
      else setDialogError(messageFor(error, "Không thể thêm thành viên."));
      return false;
    } finally {
      setDialogBusy(false);
    }
  }

  async function removeMember(member) {
    if (!project || member.role === "OWNER" || project.status !== "ACTIVE"
      || project.currentUserRole !== "OWNER") return false;
    const projectId = project.id;
    setDialogBusy(true);
    setDialogError("");
    try {
      await api(`/api/projects/${projectId}/members/${member.userId}`, { method: "DELETE" });
      if (activeProjectId.current === projectId) {
        setMembers((current) => current.filter((item) => item.userId !== member.userId));
        addToast("Đã gỡ thành viên", member.fullName);
        return true;
      }
      return false;
    } catch (error) {
      if (error.status === 401) expireSession();
      else setDialogError(messageFor(error, "Không thể gỡ thành viên."));
      return false;
    } finally {
      setDialogBusy(false);
    }
  }

  async function changeMemberRole(member, role) {
    if (!project || member.role === "OWNER" || member.role === role
      || !["MEMBER", "VIEWER"].includes(role) || project.status !== "ACTIVE"
      || project.currentUserRole !== "OWNER") return false;
    const projectId = project.id;
    setDialogBusy(true);
    setDialogError("");
    try {
      const updated = await api(`/api/projects/${projectId}/members/${member.userId}/role`, {
        method: "PATCH",
        body: { role }
      });
      if (activeProjectId.current === projectId) {
        setMembers((current) => current.map((item) => item.userId === member.userId
          ? { ...item, ...(updated || {}), role }
          : item));
        addToast("Đã đổi quyền", `${member.fullName} · ${roleLabel(role)}`);
        return true;
      }
      return false;
    } catch (error) {
      if (error.status === 401) expireSession();
      else setDialogError(messageFor(error, "Không thể đổi quyền thành viên."));
      return false;
    } finally {
      setDialogBusy(false);
    }
  }

  async function addTaskComment(payload) {
    if (!project || !editingTask || !canWriteTaskComments(project) || commentBusyAction) return false;
    const projectId = project.id;
    const taskId = editingTask.id;
    const requestId = commentRequest.current;
    setCommentBusyAction("create");
    setCommentsError("");
    try {
      const created = await api(`/api/projects/${projectId}/tasks/${taskId}/comments`, {
        method: "POST",
        body: payload
      });
      if (requestId !== commentRequest.current || activeProjectId.current !== projectId) return false;
      setTaskComments((current) => [...current, created]);
      return true;
    } catch (error) {
      if (requestId !== commentRequest.current) return false;
      if (error.status === 401) expireSession();
      else setCommentsError(error.fieldErrors?.content || messageFor(error, "Không thể thêm bình luận."));
      return false;
    } finally {
      if (requestId === commentRequest.current) setCommentBusyAction(null);
    }
  }

  async function deleteTaskComment(comment) {
    if (!project || !editingTask || !canDeleteComment(project, user, comment) || commentBusyAction) return false;
    const projectId = project.id;
    const taskId = editingTask.id;
    const requestId = commentRequest.current;
    setCommentBusyAction(`delete:${comment.id}`);
    setCommentsError("");
    try {
      await api(`/api/projects/${projectId}/tasks/${taskId}/comments/${comment.id}`, { method: "DELETE" });
      if (requestId !== commentRequest.current || activeProjectId.current !== projectId) return false;
      setTaskComments((current) => current.filter((item) => item.id !== comment.id));
      return true;
    } catch (error) {
      if (requestId !== commentRequest.current) return false;
      if (error.status === 401) expireSession();
      else setCommentsError(messageFor(error, "Không thể xóa bình luận."));
      return false;
    } finally {
      if (requestId === commentRequest.current) setCommentBusyAction(null);
    }
  }

  async function uploadTaskAttachment(file) {
    if (!project || !editingTask || !canWriteTaskAttachments(project)
      || !file || attachmentBusyAction) return false;
    const projectId = project.id;
    const taskId = editingTask.id;
    const requestId = attachmentRequest.current;
    const formData = new FormData();
    formData.append("file", file);
    setAttachmentBusyAction("upload");
    setAttachmentsError("");
    try {
      const created = await api(`/api/projects/${projectId}/tasks/${taskId}/attachments`, {
        method: "POST",
        body: formData
      });
      if (requestId !== attachmentRequest.current || activeProjectId.current !== projectId) return false;
      setTaskAttachments((current) => [...current, created]);
      addToast("Đã đính kèm tệp", created.originalName);
      return true;
    } catch (error) {
      if (requestId !== attachmentRequest.current) return false;
      if (error.status === 401) expireSession();
      else setAttachmentsError(messageFor(error, "Không thể tải tệp lên."));
      return false;
    } finally {
      if (requestId === attachmentRequest.current) setAttachmentBusyAction(null);
    }
  }

  async function downloadTaskAttachment(attachment) {
    if (!project || !editingTask || attachmentBusyAction) return false;
    const projectId = project.id;
    const taskId = editingTask.id;
    const requestId = attachmentRequest.current;
    setAttachmentBusyAction(`download:${attachment.id}`);
    setAttachmentsError("");
    try {
      await downloadFile(
        `/api/projects/${projectId}/tasks/${taskId}/attachments/${attachment.id}/download`,
        attachment.originalName
      );
      return requestId === attachmentRequest.current;
    } catch (error) {
      if (requestId !== attachmentRequest.current) return false;
      if (error.status === 401) expireSession();
      else setAttachmentsError(messageFor(error, "Không thể tải tệp xuống."));
      return false;
    } finally {
      if (requestId === attachmentRequest.current) setAttachmentBusyAction(null);
    }
  }

  async function deleteTaskAttachment(attachment) {
    if (!project || !editingTask || !canDeleteAttachment(project, user, attachment)
      || attachmentBusyAction) return false;
    const projectId = project.id;
    const taskId = editingTask.id;
    const requestId = attachmentRequest.current;
    setAttachmentBusyAction(`delete:${attachment.id}`);
    setAttachmentsError("");
    try {
      await api(`/api/projects/${projectId}/tasks/${taskId}/attachments/${attachment.id}`, {
        method: "DELETE"
      });
      if (requestId !== attachmentRequest.current || activeProjectId.current !== projectId) return false;
      setTaskAttachments((current) => current.filter((item) => item.id !== attachment.id));
      addToast("Đã xóa tệp đính kèm", attachment.originalName);
      return true;
    } catch (error) {
      if (requestId !== attachmentRequest.current) return false;
      if (error.status === 401) expireSession();
      else setAttachmentsError(messageFor(error, "Không thể xóa tệp đính kèm."));
      return false;
    } finally {
      if (requestId === attachmentRequest.current) setAttachmentBusyAction(null);
    }
  }

  async function moveTask(taskId, status, boardOrder) {
    if (!project || project.currentUserRole === "VIEWER" || movingIds.size > 0) return;
    const moveProject = project;
    const projectId = project.id;
    const before = tasks;
    setTasks((current) => moveTaskLocally(current, taskId, status, boardOrder, workflowStatuses));
    setMovingIds((current) => new Set(current).add(taskId));
    try {
      await api(`/api/projects/${projectId}/tasks/${taskId}/position`, {
        method: "PATCH",
        body: { status, boardOrder }
      });
      if (activeProjectId.current === projectId) {
        await loadBoard(moveProject);
      }
    } catch (error) {
      if (error.status === 401) {
        expireSession();
      } else if (activeProjectId.current === projectId) {
        setTasks(before);
        addToast("Chưa thể di chuyển công việc", messageFor(error), true);
      }
    } finally {
      setMovingIds((current) => {
        const next = new Set(current);
        next.delete(taskId);
        return next;
      });
    }
  }

  async function saveWorkflow(statuses) {
    if (!project || project.status !== "ACTIVE" || project.currentUserRole !== "OWNER") return;
    const workflowProject = project;
    const projectId = project.id;
    setDialogBusy(true);
    setDialogError("");
    try {
      const updated = await api(`/api/projects/${projectId}/workflow`, {
        method: "PUT",
        body: { statuses }
      });
      if (activeProjectId.current === projectId) {
        setWorkflowStatuses(Array.isArray(updated) ? updated : []);
        setDialog(null);
        await loadBoard(workflowProject);
        addToast("Đã cập nhật cột trạng thái", `${updated.length} cột trên Kanban`);
      }
    } catch (error) {
      if (error.status === 401) expireSession();
      else setDialogError(messageFor(error, "Không thể cập nhật quy trình."));
    } finally {
      setDialogBusy(false);
    }
  }

  async function handleAiSuggestionAccepted(projectId, suggestion) {
    if (!project || project.id !== projectId || activeProjectId.current !== projectId) return;
    const aiProject = project;
    await loadBoard(aiProject);
    if (activeProjectId.current === projectId) {
      addToast("Đã tạo công việc con", suggestion.createdTask?.title || "Gợi ý đã được đưa lên bảng.");
    }
  }

  async function markNotificationAsRead(notificationId) {
    const notification = notifications.find((item) => item.id === notificationId);
    if (!notification || notification.read || markingNotificationIds.has(notificationId) || markingAllNotifications) return;
    const requestId = notificationRequest.current;
    setMarkingNotificationIds((current) => new Set(current).add(notificationId));
    setNotificationsError("");
    try {
      await api(`/api/notifications/${notificationId}/read`, { method: "PATCH" });
      if (requestId === notificationRequest.current) {
        setNotifications((current) => markNotificationRead(current, notificationId, new Date().toISOString()));
      }
    } catch (error) {
      if (error.status === 401) expireSession();
      else if (requestId === notificationRequest.current) {
        setNotificationsError(messageFor(error, "Không thể đánh dấu thông báo đã đọc."));
      }
    } finally {
      setMarkingNotificationIds((current) => {
        const next = new Set(current);
        next.delete(notificationId);
        return next;
      });
    }
  }

  async function markAllNotificationsAsRead() {
    if (markingAllNotifications || markingNotificationIds.size > 0
      || !notifications.some((notification) => !notification.read)) return;
    const requestId = notificationRequest.current;
    setMarkingAllNotifications(true);
    setNotificationsError("");
    try {
      await api("/api/notifications/read-all", { method: "PATCH" });
      if (requestId === notificationRequest.current) {
        setNotifications((current) => markAllNotificationsRead(current, new Date().toISOString()));
      }
    } catch (error) {
      if (error.status === 401) expireSession();
      else if (requestId === notificationRequest.current) {
        setNotificationsError(messageFor(error, "Không thể đánh dấu tất cả đã đọc."));
      }
    } finally {
      setMarkingAllNotifications(false);
    }
  }

  async function openTaskFromNotification(notification) {
    if (!notification?.projectId) return;
    closeDialog();
    const intentId = ++dialogIntent.current;
    if (!notification.read) void markNotificationAsRead(notification.id);
    try {
      const latestProjects = await refreshProjectList();
      if (intentId !== dialogIntent.current || !latestProjects) return;
      const targetProject = latestProjects.find((item) => item.id === notification.projectId);
      if (!targetProject) {
        addToast("Không thể mở dự án", "Bạn không còn quyền truy cập dự án này.", true);
        return;
      }

      const requestedTask = notification.taskId
        ? await api(`/api/projects/${targetProject.id}/tasks/${notification.taskId}`)
        : null;
      if (intentId !== dialogIntent.current) return;
      writeSavedProjectId(targetProject.id);
      setFilters({ query: "", priority: "" });
      const nextTasks = await loadBoard(targetProject);
      if (intentId !== dialogIntent.current || !Array.isArray(nextTasks)
        || activeProjectId.current !== targetProject.id || !notification.taskId) return;

      const targetTask = nextTasks.find((item) => item.id === notification.taskId) || requestedTask;
      if (!targetTask) {
        addToast("Không tìm thấy công việc", "Công việc có thể đã bị xóa hoặc không còn khả dụng.", true);
        return;
      }
      setDialogError("");
      setEditingProject(null);
      setEditingTask(targetTask);
      setDialog("task");
      void loadTaskComments(targetProject.id, targetTask.id);
      void loadTaskAttachments(targetProject.id, targetTask.id);
    } catch (error) {
      if (intentId !== dialogIntent.current) return;
      if (error.status === 401) expireSession();
      else addToast("Không thể mở thông báo", messageFor(error, "Dữ liệu có thể đã bị xóa hoặc không còn khả dụng."), true);
    }
  }

  function openDialog(type, task = null) {
    dialogIntent.current += 1;
    setDialogError("");
    if (type === "account") {
      setAccountErrors({ profile: "", password: "", verification: "", google: "" });
      setVerificationMessage("");
    }
    commentRequest.current += 1;
    attachmentRequest.current += 1;
    setTaskComments([]);
    setCommentsLoading(false);
    setCommentsError("");
    setCommentBusyAction(null);
    setTaskAttachments([]);
    setAttachmentsLoading(false);
    setAttachmentsError("");
    setAttachmentBusyAction(null);
    setEditingTask(type === "task" ? task : null);
    setEditingProject(type === "project" ? task : null);
    setDialog(type);
    if (type === "task" && task && project?.id === task.projectId) {
      void loadTaskComments(project.id, task.id);
      void loadTaskAttachments(project.id, task.id);
    }
  }

  function closeDialog() {
    dialogIntent.current += 1;
    commentRequest.current += 1;
    attachmentRequest.current += 1;
    setDialog(null);
    setEditingTask(null);
    setEditingProject(null);
    setDialogError("");
    setAccountErrors({ profile: "", password: "", verification: "", google: "" });
    setTaskComments([]);
    setCommentsLoading(false);
    setCommentsError("");
    setCommentBusyAction(null);
    setTaskAttachments([]);
    setAttachmentsLoading(false);
    setAttachmentsError("");
    setAttachmentBusyAction(null);
  }

  return (
    <>
      <a className="skip-link" href="#main-content">Chuyển đến nội dung chính</a>
      <ToastRegion toasts={toasts} />
      {emailAction && <EmailActionView key={emailAction.id} action={emailAction} signedIn={Boolean(user)}
        onClose={() => setEmailAction(null)} onCompleted={completeEmailAction} />}
      {!emailAction && phase === "boot" && <BootScreen />}
      {!emailAction && phase === "auth" && <AuthView error={authError} busy={authBusy}
        googleEnabled={authProviders.google} onAuthenticate={authenticate} onGoogleLogin={loginWithGoogle}
        onForgotPassword={() => setEmailAction({ type: "forgot", id: ++emailActionSequence.current })} />}
      {!emailAction && phase === "app" && user && (
        <Workspace user={user} projects={projects} project={project} tasks={tasks} statuses={workflowStatuses}
          filters={filters} loading={boardLoading} boardError={boardError} movingIds={movingIds}
          notifications={notifications} notificationsLoading={notificationsLoading}
          notificationsError={notificationsError} markingNotificationIds={markingNotificationIds}
          markingAllNotifications={markingAllNotifications}
          onSelectProject={selectProject}
          onNewProject={() => openDialog("project")}
          onNewTask={() => openDialog("task")} onEditTask={(task) => openDialog("task", task)}
          onManageAccount={() => openDialog("account")}
          onManageProject={() => openDialog("project", project)} onManageMembers={() => openDialog("members")}
          onManageWorkflow={() => openDialog("workflow")}
          onRefresh={() => refreshWorkspace(true)}
          onRefreshNotifications={() => refreshWorkspace()} onMarkNotificationRead={markNotificationAsRead}
          onMarkAllNotificationsRead={markAllNotificationsAsRead} onOpenNotification={openTaskFromNotification}
          onAiAccepted={handleAiSuggestionAccepted} onSessionExpired={expireSession}
          onFilterChange={setFilters} onMove={moveTask} onLogout={logout} />
      )}
      {dialog === "project" && (
        <ProjectDialog open project={editingProject} busy={dialogBusy} error={dialogError}
          onClose={closeDialog} onSubmit={editingProject ? updateProject : createProject}
          onArchive={archiveProject} onRestore={restoreProject} />
      )}
      {dialog === "task" && (
        <TaskDialog open task={editingTask} tasks={tasks} members={members} statuses={workflowStatuses} busy={dialogBusy} error={dialogError}
          user={user} project={project} comments={taskComments} commentsLoading={commentsLoading}
          commentsError={commentsError} commentBusyAction={commentBusyAction}
          attachments={taskAttachments} attachmentsLoading={attachmentsLoading}
          attachmentsError={attachmentsError} attachmentBusyAction={attachmentBusyAction}
          readOnly={Boolean(editingTask && (!project || editingTask.projectId !== project.id
            || project.status !== "ACTIVE" || project.currentUserRole !== "OWNER"))}
          onClose={closeDialog} onSubmit={editingTask ? updateTask : createTask} onDelete={deleteTask}
          onReloadComments={() => editingTask && project && loadTaskComments(project.id, editingTask.id)}
          onAddComment={addTaskComment} onDeleteComment={deleteTaskComment}
          onReloadAttachments={() => editingTask && project && loadTaskAttachments(project.id, editingTask.id)}
          onUploadAttachment={uploadTaskAttachment} onDownloadAttachment={downloadTaskAttachment}
          onDeleteAttachment={deleteTaskAttachment} />
      )}
      {dialog === "account" && user && (
        <AccountDialog open user={user} busyAction={accountBusyAction}
          profileError={accountErrors.profile} passwordError={accountErrors.password}
          verificationError={accountErrors.verification} verificationMessage={verificationMessage}
          googleEnabled={authProviders.google} googleError={accountErrors.google}
          onClose={closeDialog} onUpdateProfile={updateAccountProfile}
          onChangePassword={changeAccountPassword} onRequestVerification={requestEmailVerification}
          onLinkGoogle={linkGoogleAccount} onUnlinkGoogle={unlinkGoogleAccount} />
      )}
      {dialog === "members" && project && (
        <MembersDialog open project={project} members={members} busy={dialogBusy} error={dialogError}
          onClose={closeDialog} onAdd={addMember} onRemove={removeMember} onChangeRole={changeMemberRole} />
      )}
      {dialog === "workflow" && project && (
        <WorkflowDialog open project={project} statuses={workflowStatuses}
          busy={dialogBusy} error={dialogError} onClose={closeDialog} onSubmit={saveWorkflow} />
      )}
    </>
  );
}

function BootScreen() {
  return (
    <section className="boot-screen" aria-label="Đang tải SmartTask">
      <div className="brand-mark brand-mark--large" aria-hidden="true">S</div>
      <div className="boot-copy"><strong>SmartTask</strong><span>Đang chuẩn bị không gian làm việc…</span></div>
      <span className="spinner" aria-hidden="true" />
    </section>
  );
}

function ToastRegion({ toasts }) {
  return (
    <div className="toast-region" aria-live="polite" aria-atomic="true">
      {toasts.map((toast) => (
        <div key={toast.id} className={`toast${toast.error ? " is-error" : ""}`} role={toast.error ? "alert" : "status"}>
          <div><strong>{toast.title}</strong>{toast.detail && <span>{toast.detail}</span>}</div>
        </div>
      ))}
    </div>
  );
}

function messageFor(error, fallback = "Vui lòng thử lại.") {
  if (!error) return fallback;
  if (error.status === 429) return accountAccessError(error);
  if (error.code === "INVALID_CREDENTIALS") return "Email hoặc mật khẩu không đúng.";
  if (error.code === "EMAIL_ALREADY_EXISTS") return "Email này đã được sử dụng.";
  if (error.code === "PROJECT_ARCHIVED") return "Dự án đã đóng và không thể chỉnh sửa.";
  return error.message || fallback;
}

function googleAccountError(error) {
  if (error?.fieldErrors?.currentPassword) return error.fieldErrors.currentPassword;
  if (error?.code === "CURRENT_PASSWORD_INVALID") return "Mật khẩu SmartTask hiện tại không đúng.";
  if (error?.code === "GOOGLE_ACCOUNT_ALREADY_LINKED") return "Tài khoản đã liên kết Google.";
  if (error?.code === "GOOGLE_ACCOUNT_NOT_LINKED") return "Tài khoản chưa liên kết Google.";
  if (error?.code === "GOOGLE_OAUTH_DISABLED") return "Đăng nhập Google chưa được bật trên máy chủ.";
  if (error?.code === "GOOGLE_LINK_NOT_ALLOWED" || error?.code === "PASSWORD_LOGIN_DISABLED") {
    return "Tài khoản này không thể thay đổi liên kết Google.";
  }
  if (error?.status === 429 || error?.status === 503) return accountAccessError(error);
  return "Không thể thay đổi liên kết Google. Vui lòng thử lại.";
}

export function safeGoogleAuthorizationPath(value) {
  if (typeof value !== "string" || !value.startsWith("/") || value.startsWith("//")) return null;
  try {
    const url = new URL(value, window.location.origin);
    if (url.origin !== window.location.origin || url.pathname !== "/oauth2/authorization/google-link" || url.hash) return null;
    return `${url.pathname}${url.search}`;
  } catch {
    return null;
  }
}

export function replaceProjectResponse(currentProjects, restoredProject) {
  return currentProjects.map((item) => item.id === restoredProject.id ? restoredProject : item);
}

export function replaceUserInProjects(currentProjects, updatedUser) {
  return currentProjects.map((item) => item.createdBy?.id === updatedUser.id
    ? { ...item, createdBy: { ...item.createdBy, fullName: updatedUser.fullName } }
    : item);
}

export function replaceUserInTasks(currentTasks, updatedUser) {
  return currentTasks.map((task) => {
    const createdBy = task.createdBy?.id === updatedUser.id
      ? { ...task.createdBy, fullName: updatedUser.fullName }
      : task.createdBy;
    const assignee = task.assignee?.id === updatedUser.id
      ? { ...task.assignee, fullName: updatedUser.fullName }
      : task.assignee;
    return createdBy === task.createdBy && assignee === task.assignee
      ? task
      : { ...task, createdBy, assignee };
  });
}

export function replaceUserInMembers(currentMembers, updatedUser) {
  return currentMembers.map((member) => member.userId === updatedUser.id
    ? { ...member, fullName: updatedUser.fullName }
    : member);
}

export function canWriteTaskComments(project) {
  return project?.status === "ACTIVE" && ["OWNER", "MEMBER"].includes(project.currentUserRole);
}

export function canWriteTaskAttachments(project) {
  return project?.status === "ACTIVE" && ["OWNER", "MEMBER"].includes(project.currentUserRole);
}

export function shouldLoadUserWorkspace(user) {
  return user?.role !== "ADMIN";
}

function consumeAuthRedirectError() {
  try {
    const url = new URL(window.location.href);
    if (url.searchParams.get("authError") !== "google") return "";
    const reason = url.searchParams.get("reason");
    url.searchParams.delete("authError");
    url.searchParams.delete("reason");
    window.history.replaceState({}, "", `${url.pathname}${url.search}${url.hash}`);
    if (reason === "link_required") {
      return "Email này đã có tài khoản SmartTask. Hãy đăng nhập bằng email và mật khẩu của tài khoản hiện có.";
    }
    if (reason === "locked") {
      return "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.";
    }
    if (reason === "link_email_mismatch") {
      return "Hãy chọn tài khoản Google có cùng email với tài khoản SmartTask này.";
    }
    if (reason === "link_conflict") {
      return "Tài khoản Google này đã được liên kết với một tài khoản SmartTask khác.";
    }
    if (reason === "link_expired") {
      return "Yêu cầu liên kết Google đã hết hạn hoặc đã được sử dụng. Hãy bắt đầu lại từ cài đặt tài khoản.";
    }
    if (reason === "already_linked") return "Tài khoản SmartTask này đã liên kết Google.";
    if (reason === "link_not_allowed") return "Tài khoản này không thể liên kết Google.";
    return "Đăng nhập hoặc liên kết Google không thành công. Vui lòng thử lại.";
  } catch {
    return "";
  }
}

function consumeGoogleLinkedSuccess() {
  try {
    const url = new URL(window.location.href);
    if (url.searchParams.get("googleLinked") !== "true") return false;
    url.searchParams.delete("googleLinked");
    window.history.replaceState({}, "", `${url.pathname}${url.search}${url.hash}`);
    return true;
  } catch {
    return false;
  }
}

function readSavedProjectId() {
  try { return window.localStorage.getItem("smarttask.projectId"); }
  catch { return null; }
}

function writeSavedProjectId(projectId) {
  try { window.localStorage.setItem("smarttask.projectId", String(projectId)); }
  catch { /* The app remains usable when storage is unavailable. */ }
}
