package kr.co.daeun.avenstudiobe.integration.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class AnthropicMoodClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClaudeProperties properties;
    private final WebClient webClient;

    public AnthropicMoodClient(
            ClaudeProperties properties,
            @Qualifier("brandMoodClaudeWebClient") WebClient webClient
    ) {
        this.properties = properties;
        this.webClient = webClient;
    }

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
                "model", properties.getModel(),
                "max_tokens", 4000,
                "temperature", 0.2,
                "system", systemPrompt,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", buildUserContent(userPrompt, pdfBytes)
                ))
        );

        String response;
        try {
            response = webClient.post()
                    .uri("/v1/messages")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(properties.getBrandMoodTimeoutSeconds()));
        } catch (WebClientResponseException e) {
            throw new IllegalStateException(
                    "Claude API 요청에 실패했습니다. status=" + e.getStatusCode().value()
                            + ", response=" + abbreviate(e.getResponseBodyAsString()),
                    e
            );
        } catch (Exception e) {
            throw new IllegalStateException("Claude API 요청 중 네트워크 또는 타임아웃 오류가 발생했습니다.", e);
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
        return properties.getModel();
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
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
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
