package kr.co.daeun.avenstudiobe.global.ai.claude;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
public class ClaudeClient {

    private final ClaudeProperties properties;
    @Qualifier("claudeWebClient")
    private final WebClient claudeWebClient;

    public String requestText(String systemPrompt, String userPrompt) {
        validateApiKey();

        if (userPrompt == null || userPrompt.isBlank()) {
            throw new ClaudeApiException("userPrompt가 비어 있습니다.");
        }

        ClaudeMessageReqDto request = new ClaudeMessageReqDto(
                properties.getModel(),
                properties.getMaxTokens(),
                0.2,
                systemPrompt,
                List.of(new ClaudeMessageDto("user", userPrompt))
        );

        try {
            ClaudeMessageRespDto response = claudeWebClient.post()
                    .uri("/v1/messages")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(body -> new ClaudeApiException(clientResponse.statusCode().value(), body))
                    )
                    .bodyToMono(ClaudeMessageRespDto.class)
                    .onErrorMap(
                            throwable -> !(throwable instanceof ClaudeApiException),
                            throwable -> new ClaudeApiException("Claude request failed.", throwable)
                    )
                    .block(Duration.ofSeconds(properties.getTimeoutSeconds()));

            return extractText(response);
        } catch (ClaudeApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ClaudeApiException("Claude response parsing failed.", e);
        }
    }

    private String extractText(ClaudeMessageRespDto response) {
        if (response == null || response.content() == null || response.content().isEmpty()) {
            throw new ClaudeApiException("Claude response content is empty.");
        }

        return response.content().stream()
                .filter(content -> "text".equals(content.type()))
                .map(ClaudeContentDto::text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElseThrow(() -> new ClaudeApiException("Claude text response is empty."));
    }

    private void validateApiKey() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new ClaudeApiException("ANTHROPIC_API_KEY is not configured.");
        }
    }
}
