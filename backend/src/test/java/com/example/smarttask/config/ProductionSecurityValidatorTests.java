package com.example.smarttask.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ProductionSecurityValidatorTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(TestConfiguration.class);

	@Test
	void doesNotApplyOutsideProductionProfile() {
		contextRunner.run(context -> assertThat(context)
				.doesNotHaveBean(ProductionSecurityValidator.class));
	}

	@Test
	void rejectsAnInsecureSessionCookieInProduction() {
		contextRunner
				.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
				.withPropertyValues("server.servlet.session.cookie.secure=false")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure())
							.hasRootCauseInstanceOf(IllegalStateException.class)
							.hasRootCauseMessage(ProductionSecurityValidator.SECURE_COOKIE_ERROR);
				});
	}

	@Test
	void rejectsAMissingSecureCookieSettingInProduction() {
		contextRunner
				.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure())
							.hasRootCauseMessage(ProductionSecurityValidator.SECURE_COOKIE_ERROR);
				});
	}

	@Test
	void acceptsASecureSessionCookieInProduction() {
		contextRunner
				.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
				.withPropertyValues("server.servlet.session.cookie.secure=true")
				.run(context -> assertThat(context)
						.hasSingleBean(ProductionSecurityValidator.class));
	}

	@Configuration(proxyBeanMethods = false)
	@Import(ProductionSecurityValidator.class)
	static class TestConfiguration {
	}
}
