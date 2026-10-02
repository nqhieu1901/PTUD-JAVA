package com.example.smarttask.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "smarttask.ai.provider", havingValue = "GROQ")
public class GroqTaskDecompositionProvider extends OpenAiTaskDecompositionProvider {

	static final String GENERATION_MODE = "GROQ";
	static final String PROVIDER_NAME = "groq";
	static final String NOTICE =
			"Gợi ý được tạo bởi mô hình AI chạy trên Groq. Hãy rà soát nội dung trước khi chấp nhận.";

	public GroqTaskDecompositionProvider(
			ObjectMapper objectMapper,
			@Value("${smarttask.ai.groq.api-key:}") String apiKey,
			@Value("${smarttask.ai.groq.model:openai/gpt-oss-20b}") String model,
			@Value("${smarttask.ai.groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
			@Value("${smarttask.ai.groq.timeout-ms:30000}") long timeoutMs) {
		super(objectMapper, apiKey, model, baseUrl, timeoutMs);
	}

	@Override
	public String generationMode() {
		return GENERATION_MODE;
	}

	@Override
	public String providerName() {
		return PROVIDER_NAME;
	}

	@Override
	public String notice() {
		return NOTICE;
	}
}
