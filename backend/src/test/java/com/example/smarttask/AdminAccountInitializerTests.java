package com.example.smarttask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.smarttask.admin.AdminAccountInitializer;
import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.SystemRole;
import com.example.smarttask.domain.User;
import com.example.smarttask.repository.UserRepository;

class AdminAccountInitializerTests {

	private final UserRepository users = mock(UserRepository.class);
	private final PasswordEncoder encoder = mock(PasswordEncoder.class);

	@Test
	void bootstrapDoesNotPromoteAnExistingRegularAccount() {
		var existing = new User("admin@example.com", "attacker-hash", "Regular user");
		when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> initialize(" ADMIN@example.com ", "bootstrap-password"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("từ chối tự nâng quyền");
		assertThat(existing.getSystemRole()).isEqualTo(SystemRole.USER);
		assertThat(existing.getPasswordHash()).isEqualTo("attacker-hash");
		verify(users, never()).saveAndFlush(any());
		verifyNoInteractions(encoder);
	}

	@Test
	void bootstrapPreservesAnExistingAdminIncludingItsPasswordAndLock() {
		var existing = new User("admin@example.com", "existing-hash", "Existing admin");
		existing.setSystemRole(SystemRole.ADMIN);
		existing.setAccountStatus(AccountStatus.LOCKED);
		when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(existing));

		initialize("admin@example.com", "different-bootstrap-password");

		assertThat(existing.getPasswordHash()).isEqualTo("existing-hash");
		assertThat(existing.getFullName()).isEqualTo("Existing admin");
		assertThat(existing.getAccountStatus()).isEqualTo(AccountStatus.LOCKED);
		verify(users, never()).saveAndFlush(any());
		verifyNoInteractions(encoder);
	}

	@Test
	void bootstrapCreatesAdminOnlyWhenEmailIsUnused() {
		when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.empty());
		when(encoder.encode("bootstrap-password")).thenReturn("encoded-password");

		initialize(" ADMIN@example.com ", "bootstrap-password");

		var saved = ArgumentCaptor.forClass(User.class);
		verify(users).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
		assertThat(saved.getValue().getSystemRole()).isEqualTo(SystemRole.ADMIN);
		assertThat(saved.getValue().getPasswordHash()).isEqualTo("encoded-password");
	}

	@Test
	void bootstrapRejectsPasswordsExceeding72Utf8BytesBeforeEncoding() {
		assertThatThrownBy(() -> initialize("admin@example.com", "ệ".repeat(25)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("72 byte UTF-8");
		verifyNoInteractions(users, encoder);
	}

	@Test
	void bootstrapWithoutCredentialsDoesNothing() {
		initialize("", "");
		verifyNoInteractions(users, encoder);
	}

	private void initialize(String email, String password) {
		new AdminAccountInitializer(users, encoder, email, password, "Configured admin")
				.run(new DefaultApplicationArguments());
	}
}
