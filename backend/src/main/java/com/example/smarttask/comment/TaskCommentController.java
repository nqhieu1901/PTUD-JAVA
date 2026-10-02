package com.example.smarttask.comment;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks/{taskId}/comments")
public class TaskCommentController {

	private final TaskCommentService taskCommentService;

	public TaskCommentController(TaskCommentService taskCommentService) {
		this.taskCommentService = taskCommentService;
	}

	@GetMapping
	public List<TaskCommentResponse> findAll(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long taskId) {
		return taskCommentService.findAll(authentication.getName(), projectId, taskId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TaskCommentResponse create(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long taskId,
			@Valid @RequestBody TaskCommentRequest request) {
		return taskCommentService.create(authentication.getName(), projectId, taskId, request);
	}

	@DeleteMapping("/{commentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long taskId,
			@PathVariable Long commentId) {
		taskCommentService.delete(authentication.getName(), projectId, taskId, commentId);
	}
}
