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
    private Integer timeoutSeconds = 30;
}
