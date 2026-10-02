package com.example.smarttask.attachment;

import org.springframework.core.io.Resource;

record AttachmentDownload(
		Resource resource,
		String originalName,
		String contentType,
		long sizeBytes) {
}
