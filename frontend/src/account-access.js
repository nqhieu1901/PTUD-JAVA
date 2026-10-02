export function newPasswordError(password, confirmation) {
  if (!password || password.length < 8) return "Mật khẩu mới cần ít nhất 8 ký tự.";
  if (new TextEncoder().encode(password).length > 72) {
    return "Mật khẩu không được vượt quá 72 byte UTF-8. Ký tự tiếng Việt có thể dùng nhiều hơn 1 byte.";
  }
  if (confirmation !== undefined && password !== confirmation) return "Mật khẩu xác nhận chưa khớp.";
  return "";
}

export function accountAccessError(error) {
  if (error?.status === 429) {
    return error.retryAfterSeconds
      ? `Bạn thao tác quá nhanh. Vui lòng thử lại sau ${error.retryAfterSeconds} giây.`
      : "Bạn thao tác quá nhanh. Vui lòng đợi một lúc rồi thử lại.";
  }
  if (error?.code === "ACCOUNT_TOKEN_INVALID") {
    return "Liên kết không hợp lệ, đã hết hạn hoặc đã được sử dụng. Vui lòng yêu cầu một liên kết mới.";
  }
  if (error?.code === "MAIL_UNAVAILABLE") {
    return "Dịch vụ gửi email chưa sẵn sàng. Vui lòng liên hệ quản trị viên hoặc thử lại sau.";
  }
  if (error?.status === 503) {
    return "Dịch vụ tài khoản tạm thời chưa sẵn sàng. Vui lòng thử lại sau.";
  }
  return "Không thể thực hiện yêu cầu. Vui lòng thử lại.";
}

export function consumeEmailAction() {
  const url = new URL(window.location.href);
  const fragment = new URLSearchParams(url.hash.slice(1));
  const parameter = fragment.has("reset-password") ? "reset-password"
    : fragment.has("verify-email") ? "verify-email" : null;
  if (!parameter) return null;
  const token = fragment.get(parameter) || "";
  // Never leave one-time credentials in browser history or persisted storage.
  window.history.replaceState({}, "", `${url.pathname}${url.search}`);
  return { type: parameter === "reset-password" ? "reset" : "verify", token };
}
