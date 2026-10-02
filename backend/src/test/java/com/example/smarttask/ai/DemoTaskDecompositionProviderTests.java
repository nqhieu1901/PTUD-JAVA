package com.example.smarttask.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DemoTaskDecompositionProviderTests {

	private final DemoTaskDecompositionProvider provider = new DemoTaskDecompositionProvider();

	@Test
	void generatesDeterministicDemoSuggestions() {
		var source = new TaskDecompositionSource("Xây dựng API", "Mô tả", 200);

		var firstGeneration = provider.generate(source, 4);
		var secondGeneration = provider.generate(source, 4);
		var first = firstGeneration.proposals();
		var second = secondGeneration.proposals();

		assertThat(first).isEqualTo(second).hasSize(4);
		assertThat(first).allSatisfy(proposal -> {
			assertThat(proposal.title()).contains("Xây dựng API");
			assertThat(proposal.description()).contains("quy tắc DEMO");
			assertThat(proposal.estimatedMinutes()).isEqualTo(50);
		});
		assertThat(provider.generationMode()).isEqualTo("DEMO");
		assertThat(provider.providerName()).isEqualTo("demo");
		assertThat(provider.modelName()).isEqualTo("demo-rules-v1");
		assertThat(provider.notice()).contains("không gọi hoặc sử dụng mô hình AI");
		assertThat(firstGeneration.inputTokens()).isZero();
		assertThat(firstGeneration.outputTokens()).isZero();
	}

	@Test
	void generatedTitleNeverExceedsTaskTitleLimit() {
		var proposals = provider.generate(
				new TaskDecompositionSource("x".repeat(255), null, null),
				6).proposals();

		assertThat(proposals)
				.allSatisfy(proposal -> assertThat(proposal.title()).hasSizeLessThanOrEqualTo(255));
	}
}
