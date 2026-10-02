// @vitest-environment jsdom
import { StrictMode, act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App, { safeGoogleAuthorizationPath } from "./App.jsx";
import EmailActionView from "./components/EmailActionView.jsx";
import { AccountDialog } from "./components/Dialogs.jsx";
import { api, ApiError } from "./api.js";
import { newPasswordError } from "./account-access.js";

vi.mock("./api.js", async (importOriginal) => ({
  ...await importOriginal(),
  api: vi.fn(),
  refreshCsrf: vi.fn().mockResolvedValue({ headerName: "X-CSRF-TOKEN", token: "test" }),
  clearCsrf: vi.fn()
}));

let container;
let root;
const user = { id: 10, fullName: "Thành viên", email: "member@example.test", role: "USER",
  passwordLoginEnabled: true, emailVerified: false, googleLinked: false };

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  api.mockReset();
  window.localStorage.clear();
  window.history.replaceState({}, "", "/");
  Object.defineProperty(HTMLDialogElement.prototype, "showModal", {
    configurable: true, value: function () { this.open = true; }
  });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  vi.restoreAllMocks();
  delete HTMLDialogElement.prototype.showModal;
});

async function render(element) { await act(async () => root.render(element)); }
function button(text) {
  return [...container.querySelectorAll("button")].find((element) => element.textContent.trim() === text);
}
async function click(element) {
  expect(element).toBeTruthy();
  await act(async () => element.click());
}
async function submit(values = {}, selector = "form") {
  const form = container.querySelector(selector);
  for (const [name, value] of Object.entries(values)) form.elements.namedItem(name).value = value;
  await act(async () => form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true })));
}
function deferred() {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
}
function sessionApi(getUser = () => null, extra = async () => null) {
  api.mockImplementation(async (path, options) => {
    if (path === "/api/auth/providers") return { google: true };
    if (path === "/api/auth/me") {
      const current = getUser();
      if (!current) throw new ApiError(401, {});
      return current;
    }
    if (path === "/api/projects" || path === "/api/notifications?unreadOnly=false") return [];
    return extra(path, options);
  });
}

describe("password recovery and verification", () => {
  it("opens recovery from login and always shows a generic email result", async () => {
    sessionApi(() => null, async () => ({ message: "sensitive-provider-response" }));
    await render(<App />);
    await click(button("Quên mật khẩu?"));
    expect(container.textContent).toContain("chỉ đăng nhập bằng Google");
    await submit({ email: "unknown@example.test" });
    expect(api).toHaveBeenCalledWith("/api/auth/forgot-password", {
      method: "POST", body: { email: "unknown@example.test" }
    });
    expect(container.textContent).toContain("Nếu email thuộc một tài khoản");
    expect(container.textContent).not.toContain("sensitive-provider-response");
    expect(container.querySelector('input[type="password"]')).toBeNull();
    expect(document.activeElement).toBe(container.querySelector('[role="status"]'));
  });

  it("scrubs a reset fragment immediately under StrictMode, posts once, and never logs in automatically", async () => {
    window.history.replaceState({}, "", "/?source=email#reset-password=one-time-reset-token");
    sessionApi();
    await render(<StrictMode><App /></StrictMode>);
    expect(window.location.hash).toBe("");
    expect(window.location.search).toBe("?source=email");
    expect(window.localStorage.length).toBe(0);
    expect(container.innerHTML).not.toContain("one-time-reset-token");
    expect(api.mock.calls.some(([path]) => path === "/api/auth/reset-password")).toBe(false);
    await submit({ newPassword: "new-password", confirmPassword: "new-password" });
    expect(api).toHaveBeenCalledWith("/api/auth/reset-password", {
      method: "POST", body: { token: "one-time-reset-token", newPassword: "new-password" }
    });
    expect(container.textContent).toContain("Đã đặt lại mật khẩu");
    expect(container.querySelector("form")).toBeNull();
    expect(api.mock.calls.some(([path]) => path === "/api/auth/login")).toBe(false);
    await click(button("Trở về đăng nhập"));
    expect(container.textContent).toContain("Đăng nhập để tiếp tục");
  });

  it("rejects a mismatched confirmation and passwords above 72 UTF-8 bytes before sending", async () => {
    await render(<EmailActionView action={{ type: "reset", token: "test-token" }}
      onClose={vi.fn()} onCompleted={vi.fn()} />);
    await submit({ newPassword: "password-a", confirmPassword: "password-b" });
    expect(container.textContent).toContain("Mật khẩu xác nhận chưa khớp");
    expect(container.querySelector('input[name="newPassword"]').getAttribute("aria-describedby"))
      .toContain("email-action-error");
    const unicodePassword = "ậ".repeat(25);
    await submit({ newPassword: unicodePassword, confirmPassword: unicodePassword });
    expect(container.textContent).toContain("72 byte UTF-8");
    expect(api).not.toHaveBeenCalled();
    expect(newPasswordError("short")).toContain("ít nhất 8 ký tự");
    expect(newPasswordError("ậ".repeat(24))).toBe("");
  });

  it("does not consume a verification link until explicit confirmation and refreshes account status", async () => {
    let verified = false;
    window.history.replaceState({}, "", "/#verify-email=one-time-verify-token");
    sessionApi(() => ({ ...user, emailVerified: verified }), async (path) => {
      if (path === "/api/auth/email-verification/confirm") verified = true;
      return {};
    });
    await render(<StrictMode><App /></StrictMode>);
    expect(window.location.hash).toBe("");
    expect(api.mock.calls.some(([path]) => path.endsWith("/confirm"))).toBe(false);
    await submit();
    expect(api).toHaveBeenCalledWith("/api/auth/email-verification/confirm", {
      method: "POST", body: { token: "one-time-verify-token" }
    });
    await click(button("Trở về không gian làm việc"));
    await click(container.querySelector('[aria-label="Mở cài đặt tài khoản"]'));
    expect(container.textContent).toContain("Email đã được xác minh.");
    expect(button("Gửi email xác minh")).toBeUndefined();
  });

  it("shows an unverified account without blocking workspace access and can request a verification email", async () => {
    sessionApi(() => user);
    await render(<App />);
    expect(container.textContent).toContain("Tạo dự án");
    await click(container.querySelector('[aria-label="Mở cài đặt tài khoản"]'));
    expect(container.textContent).toContain("Bạn vẫn có thể sử dụng tài khoản");
    await click(button("Gửi email xác minh"));
    expect(api).toHaveBeenCalledWith("/api/auth/email-verification/request", { method: "POST" });
    expect(container.textContent).toContain("Hướng dẫn xác minh đã được gửi");
  });

  it.each([
    [new ApiError(400, { code: "ACCOUNT_TOKEN_INVALID" }), "Liên kết không hợp lệ, đã hết hạn hoặc đã được sử dụng"],
    [new ApiError(503, { code: "MAIL_UNAVAILABLE" }), "Dịch vụ gửi email chưa sẵn sàng"],
    [new ApiError(503, { code: "SERVICE_UNAVAILABLE", message: "internal service detail" }), "Dịch vụ tài khoản tạm thời chưa sẵn sàng"],
    [new ApiError(429, { code: "RATE_LIMITED" }, new Headers({ "Retry-After": "45" })), "thử lại sau 45 giây"]
  ])("shows safe actionable errors for rejected requests %#", async (failure, expected) => {
    api.mockRejectedValue(failure);
    await render(<EmailActionView action={{ type: "verify", token: "test-token" }}
      onClose={vi.fn()} onCompleted={vi.fn()} />);
    await submit();
    expect(container.querySelector('[role="alert"]').textContent).toContain(expected);
    expect(container.textContent).not.toContain("internal service detail");
    expect(button("Xác nhận email").disabled).toBe(false);
  });

  it("does not expose an unexpected backend error message", async () => {
    api.mockRejectedValue(new ApiError(500, { message: "database host and stack detail" }));
    await render(<EmailActionView action={{ type: "verify", token: "test-token" }}
      onClose={vi.fn()} onCompleted={vi.fn()} />);
    await submit();
    expect(container.querySelector('[role="alert"]').textContent).toBe("Không thể thực hiện yêu cầu. Vui lòng thử lại.");
    expect(container.textContent).not.toContain("database host and stack detail");
  });

  it("scrubs an email token before the first bootstrap API request", async () => {
    window.history.replaceState({}, "", "/#verify-email=short-lived-token");
    api.mockImplementation(async (path) => {
      expect(window.location.hash).toBe("");
      if (path === "/api/auth/providers") return { google: false };
      throw new ApiError(401, {});
    });
    await render(<App />);
    expect(container.innerHTML).not.toContain("short-lived-token");
  });

  it("ignores a completed reset response after a newer email link replaces the view", async () => {
    const pending = deferred();
    window.history.replaceState({}, "", "/#reset-password=old-reset-token");
    sessionApi(() => null, (path) => path === "/api/auth/reset-password" ? pending.promise : null);
    await render(<App />);
    await submit({ newPassword: "new-password", confirmPassword: "new-password" });
    const meCallsBefore = api.mock.calls.filter(([path]) => path === "/api/auth/me").length;
    await act(async () => {
      window.history.replaceState({}, "", "/#verify-email=new-verify-token");
      window.dispatchEvent(new Event("hashchange"));
    });
    expect(container.querySelector("h1").textContent).toBe("Xác minh email");
    await act(async () => pending.resolve({}));
    expect(container.textContent).not.toContain("Đã đặt lại mật khẩu");
    expect(button("Xác nhận email")).toBeTruthy();
    expect(api.mock.calls.filter(([path]) => path === "/api/auth/me")).toHaveLength(meCallsBefore);
    expect(window.location.hash).toBe("");
  });

  it("suppresses repeat clicks while an email request is pending", async () => {
    const pending = deferred();
    api.mockReturnValue(pending.promise);
    await render(<EmailActionView action={{ type: "verify", token: "test-token" }}
      onClose={vi.fn()} onCompleted={vi.fn()} />);
    await submit();
    await submit();
    expect(api).toHaveBeenCalledTimes(1);
    expect(button("Đang xử lý…").disabled).toBe(true);
    await act(async () => pending.resolve({}));
  });

  it("does not overwrite a newer login with a slow post-verification account refresh", async () => {
    const pendingUser = deferred();
    const newUser = { ...user, id: 20, fullName: "Tài khoản mới", email: "new@example.test" };
    let meRequests = 0;
    window.history.replaceState({}, "", "/#verify-email=verify-token");
    sessionApi(() => ++meRequests === 1 ? user : pendingUser.promise,
      async (path) => path === "/api/auth/login" ? newUser : null);
    await render(<App />);
    await submit();
    await click(button("Trở về không gian làm việc"));
    await click(container.querySelector('[aria-label="Đăng xuất"]'));
    await submit({ email: newUser.email, password: "new-password" });
    expect(container.textContent).toContain("Tài khoản mới");
    await act(async () => pendingUser.resolve({ ...user, emailVerified: true }));
    expect(container.textContent).toContain("Tài khoản mới");
    expect(container.textContent).not.toContain(user.email);
  });

  it("rejects a missing reset token locally without sending an API request", async () => {
    await render(<EmailActionView action={{ type: "reset", token: "" }}
      onClose={vi.fn()} onCompleted={vi.fn()} />);
    await submit({ newPassword: "new-password", confirmPassword: "new-password" });
    expect(container.textContent).toContain("Liên kết không hợp lệ");
    expect(api).not.toHaveBeenCalled();
  });

  it("keeps a Google-only verified account free of password recovery/change actions", async () => {
    await render(<AccountDialog open user={{ ...user, emailVerified: true, passwordLoginEnabled: false }}
      onClose={vi.fn()} onUpdateProfile={vi.fn()} onChangePassword={vi.fn()} onRequestVerification={vi.fn()} />);
    expect(container.textContent).toContain("Email đã được xác minh");
    expect(container.querySelector('input[type="password"]')).toBeNull();
    expect(button("Gửi email xác minh")).toBeUndefined();
    expect(container.textContent).not.toContain("Quên mật khẩu");
  });

  it("returns to login when a successful password change invalidates authenticated sessions", async () => {
    sessionApi(() => user);
    await render(<App />);
    await click(container.querySelector('[aria-label="Mở cài đặt tài khoản"]'));
    await submit({ currentPassword: "old-password", newPassword: "new-password", confirmPassword: "new-password" },
      'form:has(input[name="currentPassword"])');
    expect(api).toHaveBeenCalledWith("/api/users/me/password", {
      method: "PUT", body: { currentPassword: "old-password", newPassword: "new-password" }
    });
    expect(container.textContent).toContain("Đăng nhập để tiếp tục");
    expect(container.textContent).toContain("Vui lòng đăng nhập lại bằng mật khẩu mới");
  });
});

describe("Google account connection", () => {
  it("only offers link management to password accounts while Google is enabled", async () => {
    const props = { open: true, onClose: vi.fn(), onUpdateProfile: vi.fn(), onChangePassword: vi.fn(),
      onRequestVerification: vi.fn(), onLinkGoogle: vi.fn(), onUnlinkGoogle: vi.fn() };

    await render(<AccountDialog {...props} user={user} googleEnabled={false} />);
    expect(container.querySelector('input[name="googleCurrentPassword"]')).toBeNull();

    await render(<AccountDialog {...props} user={user} googleEnabled />);
    expect(container.querySelector('input[name="googleCurrentPassword"]')).not.toBeNull();
    expect(button("Liên kết Google")).toBeTruthy();

    await render(<AccountDialog {...props} user={{ ...user, passwordLoginEnabled: false, googleLinked: true }} googleEnabled />);
    expect(container.querySelector('input[name="googleCurrentPassword"]')).toBeNull();
    expect(button("Hủy liên kết")).toBeUndefined();
  });

  it("suppresses a repeated link submission and rejects an unsafe authorization URL", async () => {
    const pending = deferred();
    sessionApi(() => user, (path) => path === "/api/users/me/google-link" ? pending.promise : null);
    await render(<App />);
    await click(container.querySelector('[aria-label="Mở cài đặt tài khoản"]'));
    const selector = 'form:has(input[name="googleCurrentPassword"])';
    await submit({ googleCurrentPassword: "current-password" }, selector);
    await submit({ googleCurrentPassword: "current-password" }, selector);
    expect(api.mock.calls.filter(([path]) => path === "/api/users/me/google-link")).toEqual([[
      "/api/users/me/google-link", { method: "POST", body: { currentPassword: "current-password" } }
    ]]);
    expect(button("Đang chuyển…").disabled).toBe(true);
    await act(async () => pending.resolve({ authorizationUrl: "https://attacker.example/oauth2/authorization/google-link" }));
    expect(container.querySelector('[role="alert"]').textContent).toContain("Không thể bắt đầu liên kết Google");
  });

  it("shows a safe current-password error without closing the account dialog", async () => {
    sessionApi(() => user, (path) => {
      if (path === "/api/users/me/google-link") {
        throw new ApiError(400, { code: "CURRENT_PASSWORD_INVALID", message: "backend detail" });
      }
      return null;
    });
    await render(<App />);
    await click(container.querySelector('[aria-label="Mở cài đặt tài khoản"]'));
    await submit({ googleCurrentPassword: "wrong-password" },
      'form:has(input[name="googleCurrentPassword"])');
    expect(container.querySelector('[role="alert"]').textContent).toContain("Mật khẩu SmartTask hiện tại không đúng");
    expect(container.textContent).not.toContain("backend detail");
    expect(container.querySelector("dialog")).not.toBeNull();
  });

  it("unlinks after explicit confirmation and returns to login because the session is revoked", async () => {
    const linkedUser = { ...user, googleLinked: true };
    sessionApi(() => linkedUser);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    await render(<App />);
    await click(container.querySelector('[aria-label="Mở cài đặt tài khoản"]'));
    await submit({ googleCurrentPassword: "current-password" },
      'form:has(input[name="googleCurrentPassword"])');
    expect(api).toHaveBeenCalledWith("/api/users/me/google-unlink", {
      method: "POST", body: { currentPassword: "current-password" }
    });
    expect(container.textContent).toContain("Đăng nhập để tiếp tục");
    expect(container.textContent).toContain("Đã hủy liên kết Google");
  });

  it("scrubs a successful link marker and confirms it from the refreshed account", async () => {
    window.history.replaceState({}, "", "/?source=google&googleLinked=true");
    sessionApi(() => ({ ...user, googleLinked: true }));
    await render(<App />);
    expect(window.location.search).toBe("?source=google");
    expect(container.textContent).toContain("Đã liên kết Google");
  });

  it("shows and scrubs a link callback error while preserving the signed-in workspace", async () => {
    window.history.replaceState({}, "", "/?authError=google&reason=link_email_mismatch");
    sessionApi(() => user);
    await render(<App />);
    expect(window.location.search).toBe("");
    expect(container.textContent).toContain("có cùng email");
    expect(container.textContent).toContain("Tạo dự án");
    expect(container.textContent).not.toContain("Đăng nhập để tiếp tục");
  });

  it("accepts only the dedicated same-origin relative Google-link route", () => {
    expect(safeGoogleAuthorizationPath("/oauth2/authorization/google-link?continue=1"))
      .toBe("/oauth2/authorization/google-link?continue=1");
    expect(safeGoogleAuthorizationPath("/oauth2/authorization/google")).toBeNull();
    expect(safeGoogleAuthorizationPath("//attacker.example/oauth2/authorization/google-link")).toBeNull();
    expect(safeGoogleAuthorizationPath("https://localhost/oauth2/authorization/google-link")).toBeNull();
    expect(safeGoogleAuthorizationPath("/oauth2/authorization/google-link#token")).toBeNull();
  });
});
