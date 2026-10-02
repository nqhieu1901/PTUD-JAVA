package com.example.smarttask.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.example.smarttask.web.ApiException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.ObjectMapper;

class OpenAiTaskDecompositionProviderTests {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void sendsStructuredRequestAndReadsSuggestionsAndUsage() throws Exception {
		var authorization = new AtomicReference<String>();
		var requestBody = new AtomicReference<String>();
		var outputText = objectMapper.writeValueAsString(Map.of(
				"suggestions", List.of(
						Map.of(
								"title", "Phân tích yêu cầu",
								"description", "Xác định phạm vi cần triển khai.",
								"estimatedMinutes", 45),
						Map.of(
								"title", "Kiểm thử kết quả",
								"description", "Xác nhận chức năng hoạt động đúng.",
								"estimatedMinutes", 75))));
		var responseBody = objectMapper.writeValueAsString(Map.of(
				"status", "completed",
				"output", List.of(Map.of(
						"type", "message",
						"content", List.of(Map.of(
								"type", "output_text",
								"text", outputText)))),
				"usage", Map.of("input_tokens", 41, "output_tokens", 92)));
		startServer(exchange -> {
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			writeResponse(exchange, 200, responseBody);
		});
		var provider = provider("test-api-key");

		var generation = provider.generate(
				new TaskDecompositionSource("Tích hợp thanh toán", "Mô tả", 120),
				2);

		assertThat(generation.proposals()).containsExactly(
				new TaskDecompositionProposal(
						"Phân tích yêu cầu",
						"Xác định phạm vi cần triển khai.",
						45),
				new TaskDecompositionProposal(
						"Kiểm thử kết quả",
						"Xác nhận chức năng hoạt động đúng.",
						75));
		assertThat(generation.inputTokens()).isEqualTo(41);
		assertThat(generation.outputTokens()).isEqualTo(92);
		assertThat(authorization).hasValue("Bearer test-api-key");

		var requestJson = objectMapper.readTree(requestBody.get());
		assertThat(requestJson.path("model").asText()).isEqualTo("gpt-5-mini");
		assertThat(requestJson.path("store").asBoolean()).isFalse();
		assertThat(requestJson.path("text").path("format").path("type").asText())
				.isEqualTo("json_schema");
		assertThat(requestJson.path("text").path("format").path("strict").asBoolean())
				.isTrue();
		assertThat(requestJson.path("input").asText())
				.contains("Tích hợp thanh toán", "requestedSubtasks");
	}

	@Test
	void mapsProviderRejectionToSafeApiError() throws Exception {
		startServer(exchange -> writeResponse(
				exchange,
				429,
				"{\"error\":{\"message\":\"sensitive upstream detail\"}}"));
		var provider = provider("test-api-key");

		assertThatThrownBy(() -> provider.generate(
				new TaskDecompositionSource("Task", null, 60),
				2))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getCode()).isEqualTo("AI_PROVIDER_ERROR");
					assertThat(exception.getMessage()).doesNotContain("sensitive upstream detail");
				});
	}

	@Test
	void rejectsIncompleteResponseAndBlankApiKey() throws Exception {
		startServer(exchange -> writeResponse(
				exchange,
				200,
				"{\"status\":\"completed\",\"output\":[],\"usage\":{}}"));
		var provider = provider("test-api-key");

		assertThatThrownBy(() -> provider.generate(
				new TaskDecompositionSource("Task", null, 60),
				2))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						assertThat(exception.getCode()).isEqualTo("AI_PROVIDER_INVALID_RESPONSE"));
		assertThatThrownBy(() -> provider("   "))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("OPENAI_API_KEY");
	}

	@Test
	void groqProviderUsesTheCompatibleResponsesContract() throws Exception {
		var outputText = objectMapper.writeValueAsString(Map.of(
				"suggestions", List.of(
						Map.of("title", "Bước một", "description", "Thực hiện bước một.", "estimatedMinutes", 30),
						Map.of("title", "Bước hai", "description", "Thực hiện bước hai.", "estimatedMinutes", 30))));
		var responseBody = objectMapper.writeValueAsString(Map.of(
				"status", "completed",
				"output_text", outputText,
				"usage", Map.of("input_tokens", 20, "output_tokens", 40)));
		startServer(exchange -> writeResponse(exchange, 200, responseBody));
		var provider = new GroqTaskDecompositionProvider(
				objectMapper,
				"test-groq-key",
				"openai/gpt-oss-20b",
				"http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1",
				2000);
		server.removeContext("/v1/responses");
		server.createContext("/openai/v1/responses", exchange -> writeResponse(exchange, 200, responseBody));

		var generation = provider.generate(
				new TaskDecompositionSource("Chuẩn bị báo cáo", null, 60),
				2);

		assertThat(provider.generationMode()).isEqualTo("GROQ");
		assertThat(provider.providerName()).isEqualTo("groq");
		assertThat(provider.modelName()).isEqualTo("openai/gpt-oss-20b");
		assertThat(provider.notice()).contains("Groq");
		assertThat(generation.proposals()).hasSize(2);
		assertThat(generation.inputTokens()).isEqualTo(20);
		assertThat(generation.outputTokens()).isEqualTo(40);
	}

	private OpenAiTaskDecompositionProvider provider(String apiKey) {
		return new OpenAiTaskDecompositionProvider(
				objectMapper,
				apiKey,
				"gpt-5-mini",
				"http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
				2000);
	}

	private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/responses", handler);
		server.start();
	}

	private static void writeResponse(HttpExchange exchange, int status, String body) throws IOException {
		var bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		try (var responseBody = exchange.getResponseBody()) {
			responseBody.write(bytes);
		}
	}
}
