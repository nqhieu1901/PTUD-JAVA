import { useEffect, useRef, useState } from "react";
import { api } from "../api.js";
import { accountAccessError, newPasswordError } from "../account-access.js";

export default function EmailActionView({ action, signedIn, onClose, onCompleted }) {
  const mounted = useRef(false);
  const submitting = useRef(false);
  const resultRef = useRef(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [completed, setCompleted] = useState(false);
  const forgot = action.type === "forgot";
  const reset = action.type === "reset";
  const title = forgot ? "Quên mật khẩu?" : reset ? "Đặt lại mật khẩu" : "Xác minh email";

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    if (completed) resultRef.current?.focus();
  }, [completed]);

  async function submit(event) {
    event.preventDefault();
    if (submitting.current || completed) return;
    if (!event.currentTarget.reportValidity()) return;
    const data = Object.fromEntries(new FormData(event.currentTarget));
    const validation = reset ? newPasswordError(data.newPassword, data.confirmPassword) : "";
    if (validation) { setError(validation); return; }
    if (!forgot && !action.token) {
      setError("Liên kết không hợp lệ. Vui lòng yêu cầu một liên kết mới.");
      return;
    }
    submitting.current = true;
    setBusy(true);
    setError("");
    try {
      const path = forgot ? "/api/auth/forgot-password" : reset
        ? "/api/auth/reset-password" : "/api/auth/email-verification/confirm";
      const body = forgot ? { email: data.email.trim() } : reset
        ? { token: action.token, newPassword: data.newPassword } : { token: action.token };
      await api(path, { method: "POST", body });
      if (!mounted.current) return;
      setCompleted(true);
      setMessage(forgot
        ? "Nếu email thuộc một tài khoản có thể đặt lại mật khẩu, hướng dẫn sẽ được gửi đến hộp thư. Hãy kiểm tra cả thư rác."
        : reset ? "Đã đặt lại mật khẩu. Các phiên đăng nhập cũ đã kết thúc; hãy đăng nhập bằng mật khẩu mới."
          : "Email đã được xác minh thành công.");
      if (!forgot) onCompleted(action.type);
    } catch (failure) {
      if (mounted.current) setError(accountAccessError(failure));
    } finally {
      if (mounted.current) setBusy(false);
      submitting.current = false;
    }
  }

  return (
    <main className="auth-view auth-view--email-action">
      <section className="auth-panel" aria-labelledby="email-action-heading">
        <div className="auth-panel__inner">
          <div className="auth-brand"><span className="brand-mark" aria-hidden="true">S</span><span>SmartTask</span></div>
          <div className="auth-mode-copy auth-mode-copy--login">
            <p className="eyebrow">Bảo vệ tài khoản</p>
            <h1 id="email-action-heading">{title}</h1>
            <p className="auth-subheading">{forgot ? "Nhập email bạn dùng để đăng ký SmartTask."
              : reset ? "Chọn mật khẩu mới cho tài khoản đã yêu cầu khôi phục."
                : "Nhấn xác nhận để hoàn tất xác minh địa chỉ nhận email của bạn."}</p>
          </div>
          <div className="auth-mode-sheet auth-mode-sheet--login">
            {error && <div id="email-action-error" className="form-alert" role="alert">{error}</div>}
            {message && <div ref={resultRef} className="account-access-notice" role="status" tabIndex="-1">{message}</div>}
            {!completed && <form className="auth-form" onSubmit={submit} noValidate>
              {forgot && <>
                <label className="field"><span>Email</span>
                  <input name="email" type="email" autoComplete="email" maxLength="254" required autoFocus />
                </label>
                <p className="account-access-hint">Nếu bạn chỉ đăng nhập bằng Google, hãy tiếp tục bằng Google;
                  tài khoản đó không có mật khẩu riêng tại SmartTask.</p>
              </>}
              {reset && <>
                <label className="field"><span>Mật khẩu mới</span>
                  <input name="newPassword" type="password" autoComplete="new-password" minLength="8"
                    maxLength="72" required autoFocus
                    aria-describedby={`email-action-password-hint${error ? " email-action-error" : ""}`} /></label>
                <label className="field"><span>Xác nhận mật khẩu mới</span>
                  <input name="confirmPassword" type="password" autoComplete="new-password" minLength="8"
                    maxLength="72" required
                    aria-describedby={`email-action-password-hint${error ? " email-action-error" : ""}`} /></label>
                <p id="email-action-password-hint" className="account-access-hint">Ít nhất 8 ký tự, tối đa 72 byte UTF-8.</p>
              </>}
              <button className="button button--primary button--wide" type="submit" disabled={busy} aria-busy={busy}>
                {busy ? "Đang xử lý…" : forgot ? "Gửi hướng dẫn" : reset ? "Lưu mật khẩu mới" : "Xác nhận email"}
              </button>
            </form>}
            <button className="button button--ghost button--wide account-access-back" type="button"
              disabled={busy} onClick={onClose}>
              {signedIn ? "Trở về không gian làm việc" : "Trở về đăng nhập"}
            </button>
          </div>
        </div>
      </section>
    </main>
  );
}
