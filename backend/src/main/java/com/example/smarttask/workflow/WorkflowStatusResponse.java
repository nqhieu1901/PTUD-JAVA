package com.example.smarttask.workflow;

import com.example.smarttask.domain.TaskStatus;

public record WorkflowStatusResponse(
		Long id,
		String code,
		String displayName,
		short displayOrder,
		boolean terminal) {

	public static WorkflowStatusResponse from(TaskStatus status) {
		return new WorkflowStatusResponse(
				status.getId(),
				status.getCode(),
				status.getDisplayName(),
				status.getDisplayOrder(),
				status.isTerminal());
	}
}
