package com.example.smarttask.auth;

public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Email hoặc mật khẩu không đúng");
	}
}
