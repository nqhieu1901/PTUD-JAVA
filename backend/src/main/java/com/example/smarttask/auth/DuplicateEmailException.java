package com.example.smarttask.auth;

public class DuplicateEmailException extends RuntimeException {

	public DuplicateEmailException() {
		super("Email đã được sử dụng");
	}
}
