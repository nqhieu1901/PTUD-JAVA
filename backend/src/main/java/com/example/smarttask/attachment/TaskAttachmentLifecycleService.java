package com.example.smarttask.attachment;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.smarttask.domain.Task;
import com.example.smarttask.domain.TaskAttachment;
import com.example.smarttask.repository.TaskAttachmentRepository;

@Service
public class TaskAttachmentLifecycleService {

	private static final Logger LOGGER = LoggerFactory.getLogger(TaskAttachmentLifecycleService.class);

	private final TaskAttachmentRepository taskAttachmentRepository;
	private final AttachmentStorage attachmentStorage;
	private final long orphanGraceMs;

	public TaskAttachmentLifecycleService(
			TaskAttachmentRepository taskAttachmentRepository,
			AttachmentStorage attachmentStorage,
			@Value("${smarttask.attachments.orphan-grace-ms:3600000}") long orphanGraceMs) {
		this.taskAttachmentRepository = taskAttachmentRepository;
		this.attachmentStorage = attachmentStorage;
		this.orphanGraceMs = orphanGraceMs;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void deleteAttachment(TaskAttachment attachment) {
		taskAttachmentRepository.delete(attachment);
		taskAttachmentRepository.flush();
		deleteFilesAfterCommit(List.of(attachment.getStoredName()));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void deleteAllForTask(Task task) {
		var attachments = taskAttachmentRepository.findAllByTask_Id(task.getId());
		if (attachments.isEmpty()) {
			return;
		}
		var storedNames = attachments.stream().map(TaskAttachment::getStoredName).toList();
		taskAttachmentRepository.deleteAll(attachments);
		taskAttachmentRepository.flush();
		deleteFilesAfterCommit(storedNames);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void deleteUploadedFileAfterRollback(String storedName) {
		requireTransactionSynchronization();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_COMMITTED) {
					attachmentStorage.deleteQuietly(storedName);
				}
			}
		});
	}

	@Transactional
	public ReconciliationResult reconcile(Instant now) {
		var attachments = taskAttachmentRepository.findAll();
		var knownStoredNames = new HashSet<>(taskAttachmentRepository.findAllStoredNames());
		var missingAttachments = attachments.stream()
				.filter(attachment -> !attachmentStorage.exists(attachment.getStoredName()))
				.toList();
		if (!missingAttachments.isEmpty()) {
			taskAttachmentRepository.deleteAll(missingAttachments);
			taskAttachmentRepository.flush();
		}

		var orphanStoredNames = attachmentStorage
				.findStoredNamesModifiedBefore(now.minusMillis(orphanGraceMs)).stream()
				.filter(storedName -> !knownStoredNames.contains(storedName))
				.toList();
		deleteFilesAfterCommit(orphanStoredNames);
		return new ReconciliationResult(missingAttachments.size(), orphanStoredNames.size());
	}

	private void deleteFilesAfterCommit(List<String> storedNames) {
		if (storedNames.isEmpty()) {
			return;
		}
		requireTransactionSynchronization();
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				for (var storedName : storedNames) {
					try {
						attachmentStorage.delete(storedName);
					}
					catch (RuntimeException exception) {
						LOGGER.error("Không thể xóa tệp đính kèm đã bỏ tham chiếu: {}", storedName, exception);
					}
				}
			}
		});
	}

	private void requireTransactionSynchronization() {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			throw new IllegalStateException("Vòng đời tệp đính kèm cần một transaction đang hoạt động");
		}
	}

	public record ReconciliationResult(int missingMetadataRemoved, int orphanFilesScheduled) {
	}
}
