package com.example.smarttask.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.example.smarttask.web.ApiException;

@Service
public class AccountMailDelivery {
	private static final Logger log = LoggerFactory.getLogger(AccountMailDelivery.class);

	private final String mode;
	private final String from;
	private final Path outbox;
	private final String smtpHost;
	private final ObjectProvider<JavaMailSender> mailSender;
	private final Executor executor;

	public AccountMailDelivery(ObjectProvider<JavaMailSender> mailSender,
			@Qualifier("accountMailExecutor") Executor executor,
			@Value("${smarttask.mail.mode:DISABLED}") String mode,
			@Value("${smarttask.mail.from:no-reply@smarttask.local}") String from,
			@Value("${smarttask.mail.outbox-directory:data/mail-outbox}") String outbox,
			@Value("${spring.mail.host:}") String smtpHost) {
		this.mailSender = mailSender;
		this.executor = executor;
		this.mode = mode.strip().toUpperCase(Locale.ROOT);
		this.from = from;
		this.outbox = Path.of(outbox).toAbsolutePath().normalize();
		this.smtpHost = smtpHost;
	}

	public void requireAvailable() {
		if (!isAvailable()) {
			throw unavailable();
		}
	}

	public boolean isAvailable() {
		return mode.equals("FILE")
				|| mode.equals("SMTP") && !smtpHost.isBlank() && mailSender.getIfAvailable() != null;
	}

	public void sendAfterCommit(String recipient, String subject, String body) {
		requireAvailable();
		var message = new PendingMail(recipient, subject, body);
		if (TransactionSynchronizationManager.isActualTransactionActive()
				&& TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					submit(message);
				}
			});
			return;
		}
		submit(message);
	}

	public void sendAfterCommitIfAvailable(String recipient, String subject, String body) {
		try {
			if (isAvailable()) sendAfterCommit(recipient, subject, body);
		}
		catch (RuntimeException exception) {
			// A security notification is best-effort and must never roll back a password change.
			log.warn("Account email notification could not be queued ({})",
					exception.getClass().getSimpleName());
		}
	}

	private void submit(PendingMail message) {
		try {
			executor.execute(() -> deliver(message));
		}
		catch (RuntimeException exception) {
			// Never log the recipient, body, provider message, or recovery token.
			log.warn("Account email could not be queued ({})", exception.getClass().getSimpleName());
		}
	}

	private void deliver(PendingMail pending) {
		try {
			if (mode.equals("FILE")) {
				Files.createDirectories(outbox);
				var content = "From: " + from + "\nTo: " + pending.recipient() + "\nSubject: " + pending.subject()
						+ "\nContent-Type: text/plain; charset=UTF-8\n\n" + pending.body();
				Files.writeString(outbox.resolve(UUID.randomUUID() + ".eml"), content, StandardCharsets.UTF_8,
						StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
				return;
			}
			var message = new SimpleMailMessage();
			message.setFrom(from);
			message.setTo(pending.recipient());
			message.setSubject(pending.subject());
			message.setText(pending.body());
			mailSender.getObject().send(message);
		}
		catch (IOException | RuntimeException exception) {
			// Some provider exceptions include recipients or message bodies, so log only the class.
			log.warn("Account email delivery failed ({})", exception.getClass().getSimpleName());
		}
	}

	private ApiException unavailable() {
		return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MAIL_UNAVAILABLE",
				"Dịch vụ email chưa sẵn sàng. Vui lòng thử lại sau hoặc liên hệ quản trị viên");
	}

	private record PendingMail(String recipient, String subject, String body) { }
}
