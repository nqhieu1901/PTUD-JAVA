package com.example.smarttask.ai;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
		name = "smarttask.ai.provider",
		havingValue = "DEMO",
		matchIfMissing = true)
public class DemoTaskDecompositionProvider implements TaskDecompositionProvider {

	static final String GENERATION_MODE = "DEMO";
	static final String PROVIDER_NAME = "demo";
	static final String MODEL_NAME = "demo-rules-v1";
	static final String NOTICE =
			"Chế độ DEMO dùng quy tắc cố định, không gọi hoặc sử dụng mô hình AI.";

	private static final List<List<String>> STAGES_BY_COUNT = List.of(
			List.of(),
			List.of(),
			List.of("Chuẩn bị và triển khai", "Kiểm thử và hoàn thiện"),
			List.of("Phân tích và chuẩn bị", "Triển khai", "Kiểm thử và bàn giao"),
			List.of("Phân tích yêu cầu", "Chuẩn bị", "Triển khai", "Kiểm thử và hoàn thiện"),
			List.of("Phân tích yêu cầu", "Chuẩn bị", "Triển khai", "Kiểm thử", "Hoàn thiện và bàn giao"),
			List.of("Phân tích yêu cầu", "Chuẩn bị", "Triển khai", "Kiểm thử", "Hoàn thiện tài liệu", "Rà soát và bàn giao"));

	@Override
	public String generationMode() {
		return GENERATION_MODE;
	}

	@Override
	public String providerName() {
		return PROVIDER_NAME;
	}

	@Override
	public String modelName() {
		return MODEL_NAME;
	}

	@Override
	public String notice() {
		return NOTICE;
	}

	@Override
	public TaskDecompositionGeneration generate(
			TaskDecompositionSource source,
			int maxSuggestions) {
		var stages = STAGES_BY_COUNT.get(maxSuggestions);
		var minutesPerStep = source.estimatedMinutes() == null
				? 60
				: Math.max(1, (int) Math.ceil(source.estimatedMinutes() / (double) maxSuggestions));

		var proposals = java.util.stream.IntStream.range(0, stages.size())
				.mapToObj(index -> new TaskDecompositionProposal(
						buildTitle(stages.get(index), source.title()),
						"Bước %d/%d của công việc \"%s\", được tạo bằng quy tắc DEMO."
								.formatted(index + 1, stages.size(), source.title()),
						minutesPerStep))
				.toList();
		return new TaskDecompositionGeneration(proposals, 0, 0);
	}

	private String buildTitle(String stage, String sourceTitle) {
		var prefix = stage + ": ";
		var maxSourceLength = 255 - prefix.length();
		var suffix = sourceTitle.length() <= maxSourceLength
				? sourceTitle
				: sourceTitle.substring(0, maxSourceLength);
		return prefix + suffix;
	}
}
