package com.example.smarttask.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskCommentRequest(
		@NotBlank(message = "Nội dung bình luận không được để trống")
		@Size(max = 4000, message = "Nội dung bình luận không được vượt quá 4000 ký tự")
		String content) {

	public TaskCommentRequest {
		if (content != null) {
			content = content.trim();
		}
	}
}
