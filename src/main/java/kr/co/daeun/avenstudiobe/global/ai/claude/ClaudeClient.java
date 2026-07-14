package kr.co.daeun.avenstudiobe.global.ai.claude;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class ClaudeClient {

    private final ClaudeProperties properties;
    private final WebClient claudeWebClient;
    private final Semaphore requestPermits;

    public ClaudeClient(
            ClaudeProperties properties,
            @Qualifier("claudeWebClient") WebClient claudeWebClient
    ) {
        this.properties = properties;
        this.claudeWebClient = claudeWebClient;
        this.requestPermits = new Semaphore(positiveOrDefault(properties.getMaxConcurrentRequests(), 4), true);
    }

    public String requestText(String systemPrompt, String userPrompt) {
        return requestText(systemPrompt, userPrompt, properties.getTimeoutSeconds());
    }

    public String requestText(String systemPrompt, String userPrompt, Integer timeoutSeconds) {
        validateApiKey();

        if (userPrompt == null || userPrompt.isBlank()) {
            throw new ClaudeApiException("userPrompt가 비어 있습니다.");
        }

        acquirePermit();
        try {
            return executeWithRetry(systemPrompt, userPrompt, positiveOrDefault(timeoutSeconds, 20));
        } finally {
            requestPermits.release();
        }
    }

    private String executeWithRetry(String systemPrompt, String userPrompt, int timeoutSeconds) {
        int maxRetries = nonNegativeOrDefault(properties.getMaxRetries(), 1);
        long retryDelayMillis = nonNegativeOrDefault(properties.getRetryDelayMillis(), 300);
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos();

        for (int attempt = 0; ; attempt++) {
            try {
                return executeRequest(systemPrompt, userPrompt, remainingTimeout(deadlineNanos));
            } catch (ClaudeApiException e) {
                if (attempt >= maxRetries || !isRetryable(e)) {
                    throw e;
                }
                waitBeforeRetry(retryDelayMillis, deadlineNanos);
            }
        }
    }

    private String executeRequest(String systemPrompt, String userPrompt, Duration timeout) {

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
                    .block(timeout);

            return extractText(response);
        } catch (ClaudeApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ClaudeApiException("Claude response parsing failed.", e);
        }
    }

    private void acquirePermit() {
        int waitMillis = nonNegativeOrDefault(properties.getConcurrencyWaitMillis(), 700);
        try {
            boolean acquired = requestPermits.tryAcquire(waitMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new ClaudeApiException("Claude request capacity is temporarily full.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClaudeApiException("Claude request was interrupted while waiting for capacity.", e);
        }
    }

    private boolean isRetryable(ClaudeApiException exception) {
        int statusCode = exception.getStatusCode();
        return statusCode == 429 || statusCode >= 500 || (statusCode == 0 && exception.getCause() != null);
    }

    private void waitBeforeRetry(long retryDelayMillis, long deadlineNanos) {
        long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
        if (remainingMillis <= retryDelayMillis) {
            throw new ClaudeApiException("Claude request timed out before retry.");
        }

        try {
            Thread.sleep(retryDelayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClaudeApiException("Claude request was interrupted before retry.", e);
        }
    }

    private Duration remainingTimeout(long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new ClaudeApiException("Claude request timed out.");
        }
        return Duration.ofNanos(remainingNanos);
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

    private static int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private static int nonNegativeOrDefault(Integer value, int defaultValue) {
        return value == null || value < 0 ? defaultValue : value;
    }
}
