import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../api.js";
import {
  accountStatusLabel,
  canChangeAccountStatus,
  replaceAdminUser
} from "../admin-users.js";

const PAGE_SIZE = 20;

export default function AdminUsersView({ currentUser, onSessionExpired }) {
  const requestVersion = useRef(0);
  const mounted = useRef(true);
  const [users, setUsers] = useState([]);
  const [queryInput, setQueryInput] = useState("");
  const [appliedQuery, setAppliedQuery] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busyUserId, setBusyUserId] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      requestVersion.current += 1;
    };
  }, []);

  const loadUsers = useCallback(async (nextQuery = "", nextPage = 0) => {
    const version = ++requestVersion.current;
    setLoading(true);
    setError("");
    try {
      const params = new URLSearchParams({
        query: nextQuery,
        page: String(nextPage),
        size: String(PAGE_SIZE)
      });
      const response = await api(`/api/admin/users?${params}`);
      if (!mounted.current || version !== requestVersion.current) return;
      setUsers(Array.isArray(response?.users) ? response.users : []);
      setPage(response?.page || 0);
      setTotalPages(response?.totalPages || 0);
      setTotalElements(response?.totalElements || 0);
      setAppliedQuery(nextQuery);
    } catch (requestError) {
      if (!mounted.current || version !== requestVersion.current) return;
      if (requestError.status === 401) onSessionExpired();
      else setError(adminErrorMessage(requestError, "Không thể tải danh sách người dùng."));
    } finally {
      if (mounted.current && version === requestVersion.current) setLoading(false);
    }
  }, [onSessionExpired]);

  useEffect(() => {
    loadUsers("", 0);
  }, [loadUsers]);

  function submitSearch(event) {
    event.preventDefault();
    loadUsers(queryInput.trim(), 0);
  }

  async function toggleStatus(targetUser) {
    if (!canChangeAccountStatus(currentUser, targetUser) || busyUserId !== null) return;
    const nextStatus = targetUser.status === "LOCKED" ? "ACTIVE" : "LOCKED";
    if (nextStatus === "LOCKED"
        && !window.confirm(`Khóa tài khoản ${targetUser.email}?`)) return;

    setBusyUserId(targetUser.id);
    setError("");
    try {
      const updatedUser = await api(`/api/admin/users/${targetUser.id}/status`, {
        method: "PUT",
        body: { status: nextStatus }
      });
      if (mounted.current) setUsers((current) => replaceAdminUser(current, updatedUser));
    } catch (requestError) {
      if (!mounted.current) return;
      if (requestError.status === 401) onSessionExpired();
      else setError(adminErrorMessage(requestError, "Không thể cập nhật trạng thái tài khoản."));
    } finally {
      if (mounted.current) setBusyUserId(null);
    }
  }

  return (
    <section className="admin-users-view" aria-labelledby="admin-users-heading">
      <div className="view-heading admin-users-heading">
        <div>
          <p className="eyebrow">Quản trị hệ thống</p>
          <h1 id="admin-users-heading">Tài khoản người dùng</h1>
        </div>
        <p>Theo dõi tài khoản và khóa quyền truy cập khi cần thiết.</p>
      </div>

      <form className="admin-users-toolbar" onSubmit={submitSearch}>
        <label className="search-field admin-user-search">
          <span className="search-icon" aria-hidden="true">⌕</span>
          <input value={queryInput} maxLength="100" placeholder="Tìm theo tên hoặc email"
            aria-label="Tìm người dùng" onChange={(event) => setQueryInput(event.target.value)} />
        </label>
        <button className="button button--secondary" type="submit" disabled={loading}>Tìm kiếm</button>
        <span className="admin-users-total">{totalElements} tài khoản</span>
      </form>

      {error && (
        <div className="form-alert admin-users-alert" role="alert">
          <span>{error}</span>
          <button type="button" onClick={() => loadUsers(appliedQuery, page)}>Thử lại</button>
        </div>
      )}

      <div className="admin-users-panel" aria-busy={loading}>
        {loading ? (
          <div className="admin-users-state"><span className="spinner" aria-hidden="true" />
            <strong>Đang tải tài khoản…</strong></div>
        ) : users.length === 0 ? (
          <div className="admin-users-state"><span className="admin-users-state__icon" aria-hidden="true">♙</span>
            <strong>Không tìm thấy tài khoản</strong>
            <p>Thử một tên hoặc email khác.</p></div>
        ) : (
          <div className="admin-users-table" role="table" aria-label="Danh sách tài khoản">
            <div className="admin-users-row admin-users-row--head" role="row">
              <span role="columnheader">Người dùng</span>
              <span role="columnheader">Vai trò</span>
              <span role="columnheader">Trạng thái</span>
              <span role="columnheader">Ngày tạo</span>
              <span role="columnheader">Thao tác</span>
            </div>
            {users.map((account) => {
              const canChange = canChangeAccountStatus(currentUser, account);
              const busy = busyUserId === account.id;
              return (
                <div className="admin-users-row" role="row" key={account.id}>
                  <div className="admin-user-identity" role="cell">
                    <span className="avatar avatar--small" aria-hidden="true">{initials(account.fullName)}</span>
                    <span><strong>{account.fullName}</strong><small>{account.email}</small></span>
                  </div>
                  <span role="cell" className={`system-role system-role--${account.role.toLowerCase()}`}>
                    {account.role === "ADMIN" ? "Quản trị viên" : "Người dùng"}
                  </span>
                  <span role="cell" className={`account-status account-status--${account.status.toLowerCase()}`}>
                    {accountStatusLabel(account.status)}
                  </span>
                  <time role="cell" dateTime={account.createdAt}>{formatDate(account.createdAt)}</time>
                  <span role="cell">
                    <button className={`button button--compact ${account.status === "LOCKED" ? "button--secondary" : "button--danger-quiet"}`}
                      type="button" disabled={!canChange || busyUserId !== null}
                      title={!canChange ? "Không thể tự khóa tài khoản đang sử dụng" : undefined}
                      onClick={() => toggleStatus(account)}>
                      {busy ? "Đang lưu…" : account.status === "LOCKED" ? "Mở khóa" : "Khóa"}
                    </button>
                  </span>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {totalPages > 1 && (
        <nav className="admin-users-pagination" aria-label="Phân trang người dùng">
          <button className="button button--secondary button--compact" type="button"
            disabled={loading || page === 0} onClick={() => loadUsers(appliedQuery, page - 1)}>Trang trước</button>
          <span>Trang {page + 1} / {totalPages}</span>
          <button className="button button--secondary button--compact" type="button"
            disabled={loading || page + 1 >= totalPages} onClick={() => loadUsers(appliedQuery, page + 1)}>Trang sau</button>
        </nav>
      )}
    </section>
  );
}

function adminErrorMessage(error, fallback) {
  if (error?.code === "CANNOT_LOCK_SELF") return "Bạn không thể tự khóa tài khoản đang sử dụng.";
  if (error?.status === 403) return "Bạn không có quyền quản trị hệ thống.";
  return error?.message || fallback;
}

function formatDate(value) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("vi-VN", { dateStyle: "medium" }).format(new Date(value));
}

function initials(name) {
  const parts = String(name || "").trim().split(/\s+/).filter(Boolean);
  return (parts.length === 1 ? parts[0].slice(0, 2) : parts[0][0] + parts.at(-1)[0]).toLocaleUpperCase("vi");
}
