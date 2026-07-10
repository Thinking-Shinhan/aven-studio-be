package kr.co.daeun.avenstudiobe.integration.google;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import reactor.netty.http.client.HttpClient;

@Configuration
@RequiredArgsConstructor
public class GoogleAiConfig {

    private final GoogleAiProperties properties;

    @Bean
    public RestClient googleAiRestClient() {
        Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(timeout.toMillis()))
                .responseTimeout(timeout);

        return RestClient.builder()
                .requestFactory(new ReactorClientHttpRequestFactory(httpClient))
                .build();
    }
}
