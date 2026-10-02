package com.example.smarttask.web;

import java.util.LinkedHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.example.smarttask.auth.DuplicateEmailException;
import com.example.smarttask.auth.InvalidCredentialsException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiError> apiException(ApiException exception) {
		return ResponseEntity
				.status(exception.getStatus())
				.body(new ApiError(exception.getCode(), exception.getMessage()));
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiError concurrentUpdate() {
		return new ApiError(
				"CONCURRENT_UPDATE",
				"Dữ liệu vừa được thay đổi ở một yêu cầu khác; vui lòng tải lại và thử lại");
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiError malformedRequest() {
		return new ApiError(
				"MALFORMED_REQUEST",
				"Nội dung yêu cầu không đúng định dạng");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiError malformedQueryParameter() {
		return new ApiError(
				"MALFORMED_REQUEST",
				"Tham số yêu cầu không đúng định dạng");
	}

	@ExceptionHandler(DuplicateEmailException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ApiError duplicateEmail(DuplicateEmailException exception) {
		return new ApiError("EMAIL_ALREADY_EXISTS", exception.getMessage());
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ApiError invalidCredentials(InvalidCredentialsException exception) {
		return new ApiError("INVALID_CREDENTIALS", exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiError validationFailed(MethodArgumentNotValidException exception) {
		var fieldErrors = new LinkedHashMap<String, String>();
		for (var error : exception.getBindingResult().getFieldErrors()) {
			fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		return new ApiError("VALIDATION_FAILED", "Dữ liệu không hợp lệ", fieldErrors);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
	public ApiError uploadTooLarge() {
		return new ApiError(
				"ATTACHMENT_TOO_LARGE",
				"Tệp đính kèm không được vượt quá 10 MB");
	}
}
