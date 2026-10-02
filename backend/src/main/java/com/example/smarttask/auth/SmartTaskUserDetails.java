package com.example.smarttask.auth;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.example.smarttask.domain.AccountStatus;
import com.example.smarttask.domain.User;

final class SmartTaskUserDetails extends org.springframework.security.core.userdetails.User
		implements VersionedIdentity {

	private final long credentialsVersion;

	SmartTaskUserDetails(User user) {
		super(user.getEmail(), user.getPasswordHash(), true, true, true,
				user.getAccountStatus() == AccountStatus.ACTIVE,
				List.of(new SimpleGrantedAuthority("ROLE_" + user.getSystemRole().name())));
		credentialsVersion = user.getCredentialsVersion();
	}

	@Override
	public long credentialsVersion() {
		return credentialsVersion;
	}
}
