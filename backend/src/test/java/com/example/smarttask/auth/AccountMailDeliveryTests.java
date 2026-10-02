package com.example.smarttask.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AccountMailDeliveryTests {

	@TempDir
	Path outbox;

	@Test
	void defersQueueSubmissionUntilTheDatabaseTransactionCommits() {
		var executor = mock(Executor.class);
		var delivery = delivery(executor, "FILE");
		TransactionSynchronizationManager.setActualTransactionActive(true);
		TransactionSynchronizationManager.initSynchronization();
		try {
			delivery.sendAfterCommit("member@example.com", "Subject", "Body");
			verifyNoInteractions(executor);

			var synchronization = TransactionSynchronizationManager.getSynchronizations().getFirst();
			synchronization.afterCommit();
			verify(executor).execute(any(Runnable.class));
		}
		finally {
			TransactionSynchronizationManager.clearSynchronization();
			TransactionSynchronizationManager.setActualTransactionActive(false);
		}
	}

	@Test
	void rollbackDoesNotQueueTheMessage() {
		var executor = mock(Executor.class);
		var delivery = delivery(executor, "FILE");
		TransactionSynchronizationManager.setActualTransactionActive(true);
		TransactionSynchronizationManager.initSynchronization();
		try {
			delivery.sendAfterCommit("member@example.com", "Subject", "Body");
			TransactionSynchronizationManager.getSynchronizations().getFirst()
					.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
			verifyNoInteractions(executor);
		}
		finally {
			TransactionSynchronizationManager.clearSynchronization();
			TransactionSynchronizationManager.setActualTransactionActive(false);
		}
	}

	@Test
	void fileModeWritesAUtf8MessageOutsideTheWebRoot() throws Exception {
		var delivery = delivery(Runnable::run, "FILE");
		delivery.sendAfterCommit("member@example.com", "Xác minh", "Nội dung kiểm thử");

		try (var files = Files.list(outbox)) {
			var message = Files.readString(files.findFirst().orElseThrow());
			assertThat(message).contains("To: member@example.com", "Subject: Xác minh", "Nội dung kiểm thử");
		}
	}

	@SuppressWarnings("unchecked")
	private AccountMailDelivery delivery(Executor executor, String mode) {
		ObjectProvider<JavaMailSender> sender = mock(ObjectProvider.class);
		return new AccountMailDelivery(sender, executor, mode, "no-reply@smarttask.local",
				outbox.toString(), "");
	}
}
