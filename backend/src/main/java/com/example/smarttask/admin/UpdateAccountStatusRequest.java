package com.example.smarttask.admin;

import com.example.smarttask.domain.AccountStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(
		@NotNull(message = "Trạng thái tài khoản là bắt buộc") AccountStatus status) {
}
