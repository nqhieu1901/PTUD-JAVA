package com.example.smarttask.workflow;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReplaceWorkflowRequest(
		@NotNull(message = "Danh sách trạng thái không được để trống")
		@Size(min = 2, max = 7, message = "Quy trình phải có từ 2 đến 7 trạng thái")
		List<@Valid WorkflowStatusItemRequest> statuses) {
}
