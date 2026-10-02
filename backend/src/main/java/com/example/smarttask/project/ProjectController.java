package com.example.smarttask.project;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProjectResponse create(
			Authentication authentication,
			@Valid @RequestBody ProjectRequest request) {
		return projectService.create(authentication.getName(), request);
	}

	@GetMapping
	public List<ProjectResponse> findAll(Authentication authentication) {
		return projectService.findAllForCurrentUser(authentication.getName());
	}

	@GetMapping("/{projectId}")
	public ProjectResponse findOne(
			Authentication authentication,
			@PathVariable Long projectId) {
		return projectService.findOne(authentication.getName(), projectId);
	}

	@PutMapping("/{projectId}")
	public ProjectResponse update(
			Authentication authentication,
			@PathVariable Long projectId,
			@Valid @RequestBody ProjectRequest request) {
		return projectService.update(authentication.getName(), projectId, request);
	}

	@PostMapping("/{projectId}/archive")
	public ProjectResponse archive(
			Authentication authentication,
			@PathVariable Long projectId) {
		return projectService.archive(authentication.getName(), projectId);
	}

	@PostMapping("/{projectId}/restore")
	public ProjectResponse restore(
			Authentication authentication,
			@PathVariable Long projectId) {
		return projectService.restore(authentication.getName(), projectId);
	}

	@GetMapping("/{projectId}/members")
	public List<ProjectMemberResponse> findMembers(
			Authentication authentication,
			@PathVariable Long projectId) {
		return projectService.findMembers(authentication.getName(), projectId);
	}

	@PostMapping("/{projectId}/members")
	@ResponseStatus(HttpStatus.CREATED)
	public ProjectMemberResponse addMember(
			Authentication authentication,
			@PathVariable Long projectId,
			@Valid @RequestBody AddProjectMemberRequest request) {
		return projectService.addMember(authentication.getName(), projectId, request);
	}

	@PatchMapping("/{projectId}/members/{userId}/role")
	public ProjectMemberResponse updateMemberRole(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long userId,
			@Valid @RequestBody UpdateProjectMemberRoleRequest request) {
		return projectService.updateMemberRole(
				authentication.getName(),
				projectId,
				userId,
				request);
	}

	@DeleteMapping("/{projectId}/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeMember(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long userId) {
		projectService.removeMember(authentication.getName(), projectId, userId);
	}
}
