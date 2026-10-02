package com.example.smarttask.attachment;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class AttachmentLifecycleScheduler {

	private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentLifecycleScheduler.class);

	private final TaskAttachmentLifecycleService lifecycleService;

	AttachmentLifecycleScheduler(TaskAttachmentLifecycleService lifecycleService) {
		this.lifecycleService = lifecycleService;
	}

	@Scheduled(
			fixedDelayString = "${smarttask.attachments.cleanup-scan-ms:3600000}",
			initialDelayString = "${smarttask.attachments.cleanup-initial-delay-ms:300000}")
	void reconcileAttachmentStorage() {
		var result = lifecycleService.reconcile(Instant.now());
		if (result.missingMetadataRemoved() > 0 || result.orphanFilesScheduled() > 0) {
			LOGGER.info("Đã đối soát tệp đính kèm: bỏ {} metadata thiếu file, dọn {} file mồ côi",
					result.missingMetadataRemoved(), result.orphanFilesScheduled());
		}
	}
}
