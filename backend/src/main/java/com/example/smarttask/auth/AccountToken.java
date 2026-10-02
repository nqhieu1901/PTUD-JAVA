package com.example.smarttask.auth;

import java.time.LocalDateTime;
import com.example.smarttask.domain.User;
import jakarta.persistence.*;

@Entity
@Table(name = "account_tokens", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "purpose"}))
public class AccountToken {

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
	private AccountTokenPurpose purpose;
	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;
	@Column(name = "issued_at", nullable = false)
	private LocalDateTime issuedAt;
	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;
	@Column(name = "consumed_at")
	private LocalDateTime consumedAt;
	@Column(name = "credentials_version", nullable = false)
	private long credentialsVersion;

	protected AccountToken() { }

	AccountToken(User user, AccountTokenPurpose purpose) {
		this.user = user;
		this.purpose = purpose;
	}

	void rotate(String hash, LocalDateTime now, long lifetimeMinutes) {
		tokenHash = hash;
		issuedAt = now;
		expiresAt = now.plusMinutes(lifetimeMinutes);
		consumedAt = null;
		credentialsVersion = user.getCredentialsVersion();
	}

	boolean canUse(LocalDateTime now) {
		return consumedAt == null && expiresAt.isAfter(now)
				&& credentialsVersion == user.getCredentialsVersion();
	}

	void consume(LocalDateTime now) { consumedAt = now; }
	public Long getId() { return id; }
	public User getUser() { return user; }
	public AccountTokenPurpose getPurpose() { return purpose; }
	public String getTokenHash() { return tokenHash; }
	public LocalDateTime getIssuedAt() { return issuedAt; }
	public LocalDateTime getExpiresAt() { return expiresAt; }
	public LocalDateTime getConsumedAt() { return consumedAt; }
}
