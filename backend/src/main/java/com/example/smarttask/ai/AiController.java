package com.example.smarttask.ai;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class AiController {

	private final AiDecompositionService aiDecompositionService;

	public AiController(AiDecompositionService aiDecompositionService) {
		this.aiDecompositionService = aiDecompositionService;
	}

	@PostMapping("/tasks/{taskId}/ai/decompositions")
	@ResponseStatus(HttpStatus.CREATED)
	public AiDecompositionResponse generate(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long taskId,
			@Valid @RequestBody GenerateTaskDecompositionRequest request) {
		return aiDecompositionService.generate(
				authentication.getName(),
				projectId,
				taskId,
				request);
	}

	@GetMapping("/tasks/{taskId}/ai/decompositions/latest")
	public AiDecompositionResponse latest(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long taskId) {
		return aiDecompositionService.latest(authentication.getName(), projectId, taskId);
	}

	@PostMapping("/ai/suggestions/{suggestionId}/accept")
	public AiSuggestionResponse accept(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long suggestionId) {
		return aiDecompositionService.accept(
				authentication.getName(),
				projectId,
				suggestionId);
	}

	@PostMapping("/ai/suggestions/{suggestionId}/reject")
	public AiSuggestionResponse reject(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long suggestionId) {
		return aiDecompositionService.reject(
				authentication.getName(),
				projectId,
				suggestionId);
	}
}
