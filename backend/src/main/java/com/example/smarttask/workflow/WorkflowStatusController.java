package com.example.smarttask.workflow;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects/{projectId}/workflow")
public class WorkflowStatusController {

	private final WorkflowStatusService workflowStatusService;

	public WorkflowStatusController(WorkflowStatusService workflowStatusService) {
		this.workflowStatusService = workflowStatusService;
	}

	@GetMapping
	public List<WorkflowStatusResponse> findAll(
			Authentication authentication,
			@PathVariable Long projectId) {
		return workflowStatusService.findAll(authentication.getName(), projectId);
	}

	@PutMapping
	public List<WorkflowStatusResponse> replace(
			Authentication authentication,
			@PathVariable Long projectId,
			@Valid @RequestBody ReplaceWorkflowRequest request) {
		return workflowStatusService.replace(authentication.getName(), projectId, request);
	}
}
