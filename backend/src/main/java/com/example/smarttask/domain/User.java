package com.example.smarttask.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "google_subject", unique = true, length = 255)
	private String googleSubject;

	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified;

	@Column(name = "credentials_version", nullable = false)
	private long credentialsVersion;

	@Column(name = "full_name", nullable = false, length = 150)
	private String fullName;

	@Enumerated(EnumType.STRING)
	@Column(name = "system_role", nullable = false, length = 16)
	private SystemRole systemRole = SystemRole.USER;

	@Enumerated(EnumType.STRING)
	@Column(name = "account_status", nullable = false, length = 16)
	private AccountStatus accountStatus = AccountStatus.ACTIVE;

	@Column(name = "locked_at")
	private LocalDateTime lockedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected User() {
	}

	public User(String email, String passwordHash, String fullName) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.fullName = fullName;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getGoogleSubject() {
		return googleSubject;
	}

	public void setGoogleSubject(String googleSubject) {
		this.googleSubject = googleSubject;
	}

	public boolean isEmailVerified() {
		return emailVerified;
	}

	public void setEmailVerified(boolean emailVerified) {
		this.emailVerified = emailVerified;
	}

	public long getCredentialsVersion() {
		return credentialsVersion;
	}

	public void revokeCredentials() {
		credentialsVersion++;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public SystemRole getSystemRole() {
		return systemRole;
	}

	public void setSystemRole(SystemRole systemRole) {
		this.systemRole = systemRole;
	}

	public AccountStatus getAccountStatus() {
		return accountStatus;
	}

	public void setAccountStatus(AccountStatus accountStatus) {
		this.accountStatus = accountStatus;
	}

	public LocalDateTime getLockedAt() {
		return lockedAt;
	}

	public void setLockedAt(LocalDateTime lockedAt) {
		this.lockedAt = lockedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
