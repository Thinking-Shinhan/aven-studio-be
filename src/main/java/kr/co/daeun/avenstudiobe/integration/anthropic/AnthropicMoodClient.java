package kr.co.daeun.avenstudiobe.integration.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AnthropicMoodClient {

    private static final String MESSAGES_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Value("${anthropic.api-key:}")
    private String apiKey;

    @Value("${anthropic.model:claude-haiku-4-5}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    public String analyzeBrandMood(String systemPrompt, String userPrompt) {
        return requestAnalysis(systemPrompt, userPrompt, null);
    }

    public String analyzeBrandMoodWithPdf(String systemPrompt, String userPrompt, byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF 파일이 비어 있습니다.");
        }

        return requestAnalysis(systemPrompt, userPrompt, pdfBytes);
    }

    private String requestAnalysis(String systemPrompt, String userPrompt, byte[] pdfBytes) {
        validateApiKey();

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 2000,
                "temperature", 0.2,
                "system", systemPrompt,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", buildUserContent(userPrompt, pdfBytes)
                ))
        );

        String response;
        try {
            response = restClient.post()
                    .uri(MESSAGES_URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                    "Claude API 요청에 실패했습니다. status=" + e.getStatusCode().value()
                            + ", response=" + abbreviate(e.getResponseBodyAsString()),
                    e
            );
        }

        return extractText(response);
    }

    private Object buildUserContent(String userPrompt, byte[] pdfBytes) {
        if (pdfBytes == null) {
            return userPrompt;
        }

        return List.of(
                Map.of(
                        "type", "document",
                        "source", Map.of(
                                "type", "base64",
                                "media_type", "application/pdf",
                                "data", Base64.getEncoder().encodeToString(pdfBytes)
                        )
                ),
                Map.of(
                        "type", "text",
                        "text", userPrompt
                )
        );
    }

    public String getModel() {
        return model;
    }

    private String extractText(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("content");

            if (content.isArray()) {
                for (JsonNode item : content) {
                    if ("text".equals(item.path("type").asText())) {
                        String text = item.path("text").asText(null);
                        if (text != null && !text.isBlank()) {
                            return text;
                        }
                    }
                }
            }

            throw new IllegalStateException("Claude 응답에서 텍스트 결과를 찾을 수 없습니다.");
        } catch (Exception e) {
            throw new IllegalStateException("Claude 응답 파싱에 실패했습니다.", e);
        }
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY 환경변수가 설정되어 있지 않습니다.");
        }
    }

    private String abbreviate(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        int maxLength = 1000;
        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }
}
