package kr.co.daeun.avenstudiobe.global.ai.claude;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClientRequest;

@Component
@Slf4j
public class ClaudeClient {

    private static final Consumer<String> IGNORE_STOP_REASON = ignored -> { };

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
        return requestText(ClaudeOperation.UNKNOWN, systemPrompt, userPrompt, properties.getTimeoutSeconds());
    }

    public String requestText(String systemPrompt, String userPrompt, Integer timeoutSeconds) {
        return requestText(ClaudeOperation.UNKNOWN, systemPrompt, userPrompt, timeoutSeconds);
    }

    public String requestText(
            ClaudeOperation operation,
            String systemPrompt,
            String userPrompt
    ) {
        return requestText(operation, systemPrompt, userPrompt, properties.getTimeoutSeconds());
    }

    public String requestText(
            ClaudeOperation operation,
            String systemPrompt,
            String userPrompt,
            Integer timeoutSeconds
    ) {
        validateApiKey();

        if (userPrompt == null || userPrompt.isBlank()) {
            throw new ClaudeApiException("userPrompt가 비어 있습니다.");
        }

        acquirePermit();
        try {
            ClaudeMessageReqDto request = new ClaudeMessageReqDto(
                    properties.getModel(),
                    properties.getMaxTokens(),
                    0.2,
                    systemPrompt,
                    List.of(new ClaudeMessageDto("user", userPrompt))
            );
            return executeWithRetry(
                    request,
                    positiveOrDefault(timeoutSeconds, 20),
                    IGNORE_STOP_REASON,
                    operation
            ).text();
        } finally {
            requestPermits.release();
        }
    }

    public String requestMultimodalText(
            String systemPrompt,
            List<ClaudeContentBlockDto> userContentBlocks,
            Integer maxTokens,
            Integer timeoutSeconds
    ) {
        return requestMultimodalText(
                systemPrompt,
                userContentBlocks,
                maxTokens,
                timeoutSeconds,
                IGNORE_STOP_REASON,
                ClaudeOperation.UNKNOWN
        );
    }

    public String requestMultimodalText(
            String systemPrompt,
            List<ClaudeContentBlockDto> userContentBlocks,
            Integer maxTokens,
            Integer timeoutSeconds,
            Consumer<String> stopReasonObserver
    ) {
        return requestMultimodalText(
                systemPrompt,
                userContentBlocks,
                maxTokens,
                timeoutSeconds,
                stopReasonObserver,
                ClaudeOperation.UNKNOWN
        );
    }

    private String requestMultimodalText(
            String systemPrompt,
            List<ClaudeContentBlockDto> userContentBlocks,
            Integer maxTokens,
            Integer timeoutSeconds,
            Consumer<String> stopReasonObserver,
            ClaudeOperation operation
    ) {
        ClaudeTextResponse response = requestMultimodalResponse(
                systemPrompt,
                userContentBlocks,
                maxTokens,
                timeoutSeconds,
                stopReasonObserver,
                operation
        );
        return response.text();
    }

    public ClaudeTextResponse requestMultimodalTextWithMetadata(
            String systemPrompt,
            List<ClaudeContentBlockDto> userContentBlocks,
            Integer maxTokens,
            Integer timeoutSeconds
    ) {
        return requestMultimodalResponse(
                systemPrompt,
                userContentBlocks,
                maxTokens,
                timeoutSeconds,
                IGNORE_STOP_REASON,
                ClaudeOperation.UNKNOWN
        );
    }

    public ClaudeTextResponse requestMultimodalTextWithMetadata(
            ClaudeOperation operation,
            String systemPrompt,
            List<ClaudeContentBlockDto> userContentBlocks,
            Integer maxTokens,
            Integer timeoutSeconds
    ) {
        return requestMultimodalResponse(
                systemPrompt,
                userContentBlocks,
                maxTokens,
                timeoutSeconds,
                IGNORE_STOP_REASON,
                operation
        );
    }

    private ClaudeTextResponse requestMultimodalResponse(
            String systemPrompt,
            List<ClaudeContentBlockDto> userContentBlocks,
            Integer maxTokens,
            Integer timeoutSeconds,
            Consumer<String> stopReasonObserver,
            ClaudeOperation operation
    ) {
        validateApiKey();

        if (userContentBlocks == null || userContentBlocks.isEmpty()) {
            throw new ClaudeApiException("userContentBlocks가 비어 있습니다.");
        }
        if (maxTokens == null || maxTokens <= 0) {
            throw new ClaudeApiException("maxTokens는 양수여야 합니다.");
        }

        acquirePermit();
        try {
            ClaudeMultimodalMessageReqDto request = new ClaudeMultimodalMessageReqDto(
                    properties.getModel(),
                    maxTokens,
                    0.2,
                    systemPrompt,
                    List.of(new ClaudeMultimodalMessageDto("user", userContentBlocks))
            );
            int effectiveTimeout = positiveOrDefault(
                    timeoutSeconds,
                    positiveOrDefault(properties.getReviewImageTimeoutSeconds(), 30)
            );
            Consumer<String> observer = stopReasonObserver == null
                    ? IGNORE_STOP_REASON
                    : stopReasonObserver;
            return executeWithRetry(request, effectiveTimeout, observer, operation);
        } finally {
            requestPermits.release();
        }
    }

    private ClaudeTextResponse executeWithRetry(
            Object request,
            int timeoutSeconds,
            Consumer<String> stopReasonObserver,
            ClaudeOperation operation
    ) {
        int maxRetries = nonNegativeOrDefault(properties.getMaxRetries(), 1);
        long retryDelayMillis = nonNegativeOrDefault(properties.getRetryDelayMillis(), 300);
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos();

        for (int attempt = 0; ; attempt++) {
            try {
                return executeRequest(
                        request,
                        remainingTimeout(deadlineNanos),
                        stopReasonObserver,
                        operation,
                        attempt + 1
                );
            } catch (ClaudeApiException e) {
                if (attempt >= maxRetries || !isRetryable(e)) {
                    throw e;
                }
                waitBeforeRetry(retryDelayMillis, deadlineNanos);
            }
        }
    }

    private ClaudeTextResponse executeRequest(
            Object request,
            Duration timeout,
            Consumer<String> stopReasonObserver,
            ClaudeOperation operation,
            int attempt
    ) {
        try {
            ResponseEntity<ClaudeMessageRespDto> responseEntity = claudeWebClient.post()
                    .uri("/v1/messages")
                    .httpRequest(clientRequest -> {
                        HttpClientRequest nativeRequest = clientRequest.getNativeRequest();
                        nativeRequest.responseTimeout(timeout);
                    })
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .map(body -> new ClaudeApiException(clientResponse.statusCode().value(), body))
                    )
                    .toEntity(ClaudeMessageRespDto.class)
                    .onErrorMap(
                            throwable -> !(throwable instanceof ClaudeApiException),
                            throwable -> new ClaudeApiException("Claude request failed.", throwable)
                    )
                    .block(timeout.plusSeconds(2));

            ClaudeMessageRespDto response = responseEntity == null ? null : responseEntity.getBody();
            String stopReason = response == null ? null : response.stopReason();
            stopReasonObserver.accept(stopReason);
            ClaudeUsageDto usage = response == null ? null : response.usage();
            String requestId = responseEntity == null
                    ? null
                    : responseEntity.getHeaders().getFirst("request-id");
            logUsage(operation, attempt, stopReason, requestId, usage);
            return new ClaudeTextResponse(
                    extractText(response),
                    stopReason,
                    requestId,
                    usage
            );
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

    private void logUsage(
            ClaudeOperation operation,
            int attempt,
            String stopReason,
            String requestId,
            ClaudeUsageDto usage
    ) {
        ClaudeOperation effectiveOperation = operation == null ? ClaudeOperation.UNKNOWN : operation;
        if (usage == null) {
            log.warn(
                    "Claude usage missing. operation={}, model={}, attempt={}, stopReason={}, requestId={}",
                    effectiveOperation,
                    properties.getModel(),
                    attempt,
                    stopReason,
                    requestId
            );
            return;
        }

        log.info(
                "Claude usage. operation={}, model={}, attempt={}, inputTokens={}, outputTokens={}, "
                        + "cacheCreationInputTokens={}, cacheReadInputTokens={}, stopReason={}, requestId={}",
                effectiveOperation,
                properties.getModel(),
                attempt,
                zeroIfNull(usage.inputTokens()),
                zeroIfNull(usage.outputTokens()),
                zeroIfNull(usage.cacheCreationInputTokens()),
                zeroIfNull(usage.cacheReadInputTokens()),
                stopReason,
                requestId
        );
    }

    private static long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    private static int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private static int nonNegativeOrDefault(Integer value, int defaultValue) {
        return value == null || value < 0 ? defaultValue : value;
    }

    public record ClaudeTextResponse(
            String text,
            String stopReason,
            String requestId,
            ClaudeUsageDto usage
    ) {
        public ClaudeTextResponse(String text, String stopReason, String requestId) {
            this(text, stopReason, requestId, null);
        }
    }
}
