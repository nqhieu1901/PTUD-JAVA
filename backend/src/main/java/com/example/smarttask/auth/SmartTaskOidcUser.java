package com.example.smarttask.auth;

import java.util.Collection;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

final class SmartTaskOidcUser implements OidcUser, VersionedIdentity {

	private final OidcUser delegate;
	private final String name;
	private final Collection<? extends GrantedAuthority> authorities;
	private final long credentialsVersion;

	SmartTaskOidcUser(
			OidcUser delegate,
			String name,
			Collection<? extends GrantedAuthority> authorities,
			long credentialsVersion) {
		this.delegate = delegate;
		this.name = name;
		this.authorities = authorities;
		this.credentialsVersion = credentialsVersion;
	}

	@Override
	public long credentialsVersion() {
		return credentialsVersion;
	}

	@Override
	public Map<String, Object> getClaims() {
		return delegate.getClaims();
	}

	@Override
	public OidcUserInfo getUserInfo() {
		return delegate.getUserInfo();
	}

	@Override
	public OidcIdToken getIdToken() {
		return delegate.getIdToken();
	}

	@Override
	public Map<String, Object> getAttributes() {
		return delegate.getAttributes();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getName() {
		return name;
	}
}
