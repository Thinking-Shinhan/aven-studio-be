package kr.co.daeun.avenstudiobe.global.ai.claude;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
@RequiredArgsConstructor
public class ClaudeConfig {

    private static final String BASE_URL = "https://api.anthropic.com";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final ClaudeProperties properties;

    @Bean
    public WebClient claudeWebClient() {
        return buildWebClient(Duration.ofSeconds(properties.getTimeoutSeconds()));
    }

    @Bean
    @Qualifier("brandMoodClaudeWebClient")
    public WebClient brandMoodClaudeWebClient() {
        return buildWebClient(Duration.ofSeconds(properties.getBrandMoodTimeoutSeconds()));
    }

    private WebClient buildWebClient(Duration timeout) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(timeout.toMillis()))
                .responseTimeout(timeout);

        return WebClient.builder()
                .baseUrl(BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("x-api-key", properties.getApiKey())
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
