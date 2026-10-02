package com.example.smarttask.task;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.smarttask.domain.TaskPriority;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
public class TaskController {

	private final TaskService taskService;

	public TaskController(TaskService taskService) {
		this.taskService = taskService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TaskResponse create(
			Authentication authentication,
			@PathVariable Long projectId,
			@Valid @RequestBody TaskRequest request) {
		return taskService.create(authentication.getName(), projectId, request);
	}

	@GetMapping
	public List<TaskResponse> findAll(
			Authentication authentication,
			@PathVariable Long projectId,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) TaskPriority priority,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
			@RequestParam(required = false) Long assigneeId) {
		var filter = new TaskFilter(q, status, priority, dueFrom, dueTo, assigneeId);
		return taskService.findAll(authentication.getName(), projectId, filter);
	}

	@GetMapping("/{taskId}")
	public TaskResponse findOne(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long taskId) {
		return taskService.findOne(authentication.getName(), projectId, taskId);
	}

	@PutMapping("/{taskId}")
	public TaskResponse update(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long taskId,
			@Valid @RequestBody TaskRequest request) {
		return taskService.update(authentication.getName(), projectId, taskId, request);
	}

	@PatchMapping("/{taskId}/status")
	public TaskResponse updateStatus(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long taskId,
			@Valid @RequestBody TaskStatusUpdateRequest request) {
		return taskService.updateStatus(authentication.getName(), projectId, taskId, request);
	}

	@PatchMapping("/{taskId}/position")
	public TaskResponse updatePosition(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long taskId,
			@Valid @RequestBody TaskPositionUpdateRequest request) {
		return taskService.updatePosition(authentication.getName(), projectId, taskId, request);
	}

	@DeleteMapping("/{taskId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long taskId) {
		taskService.delete(authentication.getName(), projectId, taskId);
	}
}
