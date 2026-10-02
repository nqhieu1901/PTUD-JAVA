import { useState } from "react";
import { newPasswordError } from "../account-access.js";

export default function AuthView({ error, busy, googleEnabled, onAuthenticate, onGoogleLogin, onForgotPassword }) {
  const [mode, setMode] = useState("login");
  const [showPassword, setShowPassword] = useState(false);
  const [localError, setLocalError] = useState("");

  function switchMode(nextMode) {
    setMode(nextMode);
    setShowPassword(false);
    setLocalError("");
  }

  function submit(event) {
    event.preventDefault();
    if (!event.currentTarget.reportValidity()) return;
    const data = new FormData(event.currentTarget);
    const validation = mode === "register" ? newPasswordError(data.get("password")) : "";
    setLocalError(validation);
    if (validation) return;
    onAuthenticate(mode, Object.fromEntries(data));
  }

  const registering = mode === "register";
  return (
    <main className="auth-view">
      <section className="auth-story" aria-labelledby="brand-heading">
        <div className="auth-brand"><span className="brand-mark" aria-hidden="true">S</span><span>SmartTask</span></div>
        <div className="auth-story__content">
          <p className="eyebrow">Làm việc rõ ràng hơn</p>
          <h1 id="brand-heading">Biến kế hoạch thành<br />việc đã hoàn thành.</h1>
          <p>Sắp xếp công việc theo luồng, tập trung vào ưu tiên, cùng nhóm tiến về phía trước.</p>
          <div className="mini-board" aria-hidden="true">
            <div className="mini-column"><span>Việc cần làm</span><i /><i /></div>
            <div className="mini-column mini-column--active"><span>Đang thực hiện</span><i /></div>
            <div className="mini-column"><span>Hoàn thành</span><i className="done" /><i className="done" /></div>
          </div>
        </div>
        <p className="auth-story__footer">Một nhịp làm việc nhẹ nhàng cho mọi dự án.</p>
      </section>

      <section className="auth-panel" aria-labelledby="auth-heading">
        <div className="auth-panel__inner">
          <div className="mobile-brand"><span className="brand-mark" aria-hidden="true">S</span><span>SmartTask</span></div>
          <div key={`copy-${mode}`} className={`auth-mode-copy auth-mode-copy--${mode}`}>
            <p className="eyebrow">Chào mừng bạn</p>
            <h2 id="auth-heading">{registering ? "Tạo tài khoản mới" : "Đăng nhập để tiếp tục"}</h2>
            <p className="auth-subheading">
              {registering ? "Bắt đầu không gian làm việc của riêng bạn." : "Quay lại bảng công việc của bạn."}
            </p>
          </div>
          <div className={`auth-tabs${registering ? " is-register" : ""}`} role="tablist" aria-label="Tài khoản">
            <button className={`auth-tab${!registering ? " is-active" : ""}`} type="button" role="tab"
              aria-selected={!registering} onClick={() => switchMode("login")}>Đăng nhập</button>
            <button className={`auth-tab${registering ? " is-active" : ""}`} type="button" role="tab"
              aria-selected={registering} onClick={() => switchMode("register")}>Đăng ký</button>
          </div>
          <div key={mode} className={`auth-mode-sheet auth-mode-sheet--${mode}`}>
            {(localError || error) && <div className="form-alert" role="alert">{localError || error}</div>}
            <button className="google-login-button" type="button" disabled={busy || !googleEnabled}
              title={googleEnabled ? "Đăng nhập hoặc tạo tài khoản bằng Google" : "Chưa cấu hình đăng nhập Google"}
              onClick={onGoogleLogin}>
              <GoogleMark />
              <span>{googleEnabled ? "Tiếp tục với Google" : "Google chưa được cấu hình"}</span>
            </button>
            <div className="auth-divider"><span>hoặc dùng email</span></div>
            <form className="auth-form" onSubmit={submit} noValidate>
              {registering && (
                <label className="field">
                  <span>Họ và tên</span>
                  <input name="fullName" autoComplete="name" maxLength="150" placeholder="Nguyễn Minh An" required />
                </label>
              )}
              <label className="field">
                <span>Email</span>
                <input name="email" type="email" autoComplete="email" maxLength="254" placeholder="ban@example.com" required />
              </label>
              <label className="field">
                <span>Mật khẩu</span>
                <span className="password-field">
                  <input name="password" type={showPassword ? "text" : "password"}
                    autoComplete={registering ? "new-password" : "current-password"}
                    minLength={registering ? 8 : undefined} maxLength="72"
                    placeholder={registering ? "Tối thiểu 8 ký tự" : "Nhập mật khẩu"} required />
                  <button className="password-toggle" type="button"
                    aria-label={showPassword ? "Ẩn mật khẩu" : "Hiển thị mật khẩu"}
                    onClick={() => setShowPassword((visible) => !visible)}>{showPassword ? "Ẩn" : "Hiện"}</button>
                </span>
              </label>
              {!registering && <button className="auth-forgot-link" type="button" disabled={busy}
                onClick={onForgotPassword}>Quên mật khẩu?</button>}
              <button className="button button--primary button--wide" type="submit" disabled={busy} aria-busy={busy}>
                <span>{busy ? "Đang xử lý…" : registering ? "Tạo tài khoản" : "Đăng nhập"}</span>
                {!busy && <span aria-hidden="true">→</span>}
              </button>
            </form>
          </div>
        </div>
      </section>
    </main>
  );
}

function GoogleMark() {
  return (
    <svg className="google-mark" aria-hidden="true" viewBox="0 0 24 24">
      <path fill="#4285F4" d="M21.6 12.23c0-.71-.06-1.4-.18-2.07H12v3.92h5.38a4.6 4.6 0 0 1-2 3.02v2.54h3.24c1.9-1.75 2.98-4.33 2.98-7.41Z" />
      <path fill="#34A853" d="M12 22c2.7 0 4.97-.9 6.62-2.36l-3.24-2.54c-.9.6-2.05.96-3.38.96-2.61 0-4.82-1.76-5.61-4.13H3.04v2.62A10 10 0 0 0 12 22Z" />
      <path fill="#FBBC05" d="M6.39 13.93A6.02 6.02 0 0 1 6.07 12c0-.67.12-1.32.32-1.93V7.45H3.04A10 10 0 0 0 2 12c0 1.63.39 3.17 1.04 4.55l3.35-2.62Z" />
      <path fill="#EA4335" d="M12 5.94c1.47 0 2.79.5 3.83 1.5l2.87-2.87A9.63 9.63 0 0 0 12 2a10 10 0 0 0-8.96 5.45l3.35 2.62C7.18 7.7 9.39 5.94 12 5.94Z" />
    </svg>
  );
}
