package kr.co.daeun.avenstudiobe.global.ai.claude;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "anthropic")
public class ClaudeProperties {

    private String apiKey;
    private String model = "claude-haiku-4-5";
    private Integer maxTokens = 2048;
    private Integer timeoutSeconds = 20;
    private Integer brandMoodTimeoutSeconds = 120;
    private Integer imagePlacementTimeoutSeconds = 15;
    private Integer maxConcurrentRequests = 4;
    private Integer concurrencyWaitMillis = 700;
    private Integer maxRetries = 1;
    private Integer retryDelayMillis = 300;
    private Integer reviewMaxImages = 12;
    private Integer reviewImageMaxEdge = 512;
    private Integer reviewImageTimeoutSeconds = 30;
    private Integer reviewMaxIssues = 30;
    private Integer reviewMaxTokens = 8192;
}
