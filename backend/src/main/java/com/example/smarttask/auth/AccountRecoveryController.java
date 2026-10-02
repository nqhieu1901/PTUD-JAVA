package com.example.smarttask.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.example.smarttask.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
public class AccountRecoveryController {

	private final AccountTokenService tokens;
	private final AccountMailDelivery delivery;

	public AccountRecoveryController(AccountTokenService tokens, AccountMailDelivery delivery) {
		this.tokens = tokens;
		this.delivery = delivery;
	}

	@PostMapping("/forgot-password")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		delivery.requireAvailable();
		try {
			tokens.requestPasswordReset(request.email());
		}
		catch (ApiException exception) {
			// Once configured, transport failure must not reveal whether an email exists.
			// The transactional service has rolled the token back; clients can retry later.
			if (!exception.getCode().equals("MAIL_UNAVAILABLE")) throw exception;
		}
		return new MessageResponse("Nếu email thuộc tài khoản có thể đặt lại mật khẩu, hướng dẫn sẽ được gửi. Vui lòng kiểm tra hộp thư và thư rác");
	}

	@PostMapping("/reset-password")
	public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		tokens.resetPassword(request.token(), request.newPassword());
		return new MessageResponse("Đã đặt lại mật khẩu. Vui lòng đăng nhập lại bằng mật khẩu mới");
	}

	@PostMapping("/email-verification/request")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public MessageResponse requestVerification(Authentication authentication) {
		tokens.requestVerification(authentication.getName());
		return new MessageResponse("Nếu email chưa được xác minh, hướng dẫn xác minh sẽ được gửi. Vui lòng kiểm tra hộp thư và thư rác");
	}

	@PostMapping("/email-verification/confirm")
	public MessageResponse confirmVerification(@Valid @RequestBody TokenRequest request) {
		tokens.confirmVerification(request.token());
		return new MessageResponse("Email đã được xác minh thành công");
	}

	public record MessageResponse(String message) { }

	public record ForgotPasswordRequest(
			@NotBlank @Email @Size(max = 254) String email) {
		public ForgotPasswordRequest { if (email != null) email = email.strip(); }
	}

	public record ResetPasswordRequest(
			@NotBlank @Size(max = 128) String token,
			@NotBlank @Size(min = 8, max = 72) String newPassword) { }

	public record TokenRequest(@NotBlank @Size(max = 128) String token) { }
}
