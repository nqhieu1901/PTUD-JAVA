package com.example.smarttask.attachment;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks/{taskId}/attachments")
public class TaskAttachmentController {

	private final TaskAttachmentService taskAttachmentService;

	public TaskAttachmentController(TaskAttachmentService taskAttachmentService) {
		this.taskAttachmentService = taskAttachmentService;
	}

	@GetMapping
	public List<TaskAttachmentResponse> findAll(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long taskId) {
		return taskAttachmentService.findAll(authentication.getName(), projectId, taskId);
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public TaskAttachmentResponse upload(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long taskId,
			@RequestPart("file") MultipartFile file) {
		return taskAttachmentService.upload(authentication.getName(), projectId, taskId, file);
	}

	@GetMapping("/{attachmentId}/download")
	public ResponseEntity<Resource> download(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long taskId,
			@PathVariable Long attachmentId) {
		var download = taskAttachmentService.download(
				authentication.getName(), projectId, taskId, attachmentId);
		var disposition = ContentDisposition.attachment()
				.filename(download.originalName(), StandardCharsets.UTF_8)
				.build();
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.contentType(MediaType.parseMediaType(download.contentType()))
				.contentLength(download.sizeBytes())
				.body(download.resource());
	}

	@DeleteMapping("/{attachmentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(
			Authentication authentication,
			@PathVariable Long projectId,
			@PathVariable Long taskId,
			@PathVariable Long attachmentId) {
		taskAttachmentService.delete(authentication.getName(), projectId, taskId, attachmentId);
	}
}
