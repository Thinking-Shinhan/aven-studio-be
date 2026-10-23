package kr.co.daeun.avenstudiobe.global.ai.claude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

class ClaudeClientTest {

    @Test
    void retriesOnceAfterTransientServerError() {
        AtomicInteger requestCount = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    if (requestCount.incrementAndGet() == 1) {
                        return Mono.just(response(HttpStatus.INTERNAL_SERVER_ERROR, "server error"));
                    }
                    return Mono.just(response(HttpStatus.OK, "{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}"));
                })
                .build();

        ClaudeClient client = new ClaudeClient(properties(), webClient);

        assertEquals("ok", client.requestText("system", "user"));
        assertEquals(2, requestCount.get());
    }

    @Test
    void doesNotRetryNonTransientClientError() {
        AtomicInteger requestCount = new AtomicInteger();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    requestCount.incrementAndGet();
                    return Mono.just(response(HttpStatus.BAD_REQUEST, "bad request"));
                })
                .build();

        ClaudeClient client = new ClaudeClient(properties(), webClient);

        assertThrows(ClaudeApiException.class, () -> client.requestText("system", "user"));
        assertEquals(1, requestCount.get());
    }

    @Test
    void overridesConnectorResponseTimeoutForEachRequest() {
        DisposableServer server = delayedClaudeServer(Duration.ofMillis(300));
        try {
            WebClient webClient = reactorNettyWebClient(server, Duration.ofMillis(100));
            ClaudeProperties properties = properties();
            properties.setMaxRetries(0);
            ClaudeClient client = new ClaudeClient(properties, webClient);

            assertEquals(
                    "ok",
                    client.requestMultimodalText(
                            "system",
                            List.of(new ClaudeTextContentBlockDto("user")),
                            8192,
                            1
                    )
            );
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void mapsNettyResponseTimeoutAsRequestFailure() {
        DisposableServer server = delayedClaudeServer(Duration.ofMillis(1500));
        try {
            WebClient webClient = reactorNettyWebClient(server, Duration.ofSeconds(5));
            ClaudeProperties properties = properties();
            properties.setMaxRetries(0);
            ClaudeClient client = new ClaudeClient(properties, webClient);

            ClaudeApiException exception = assertThrows(
                    ClaudeApiException.class,
                    () -> client.requestText("system", "user", 1)
            );

            assertEquals("Claude request failed.", exception.getMessage());
            assertTrue(!exception.getMessage().contains("parsing failed"));
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void preservesLegacyRequestTextWireFormatAndDefaultMaxTokens() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        DisposableServer server = HttpServer.create()
                .port(0)
                .route(routes -> routes.post(
                        "/v1/messages",
                        (request, response) -> request.receive()
                                .aggregate()
                                .asString()
                                .flatMap(body -> {
                                    requestBody.set(body);
                                    return response
                                            .header("Content-Type", "application/json")
                                            .sendString(Mono.just(
                                                    "{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}"
                                            ))
                                            .then();
                                })
                ))
                .bindNow();
        try {
            ClaudeProperties properties = properties();
            properties.setModel("test-model");
            properties.setMaxTokens(2048);
            properties.setMaxRetries(0);
            ClaudeClient client = new ClaudeClient(
                    properties,
                    reactorNettyWebClient(server, Duration.ofSeconds(20))
            );

            assertEquals("ok", client.requestText("legacy-system", "legacy-user"));

            JsonNode json = new ObjectMapper().readTree(requestBody.get());
            assertEquals(2048, json.path("max_tokens").asInt());
            assertEquals("legacy-system", json.path("system").asText());
            assertEquals("user", json.path("messages").get(0).path("role").asText());
            assertEquals("legacy-user", json.path("messages").get(0).path("content").asText());
        } finally {
            server.disposeNow();
        }
    }

    @Test
    @Timeout(40)
    void reviewTimeoutOverrideWaitsPastTheTwentySecondBeanLimitForA25SecondResponse() {
        DisposableServer server = delayedClaudeServer(Duration.ofSeconds(25));
        try {
            WebClient webClient = reactorNettyWebClient(server, Duration.ofSeconds(20));
            ClaudeProperties properties = properties();
            properties.setMaxRetries(0);
            properties.setReviewImageTimeoutSeconds(30);
            ClaudeClient client = new ClaudeClient(properties, webClient);
            long startedAt = System.nanoTime();

            String response = client.requestMultimodalText(
                    "system",
                    List.of(new ClaudeTextContentBlockDto("user")),
                    8192,
                    properties.getReviewImageTimeoutSeconds()
            );

            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
            assertEquals("ok", response);
            assertTrue(elapsed.compareTo(Duration.ofSeconds(24)) >= 0);
            assertTrue(elapsed.compareTo(Duration.ofSeconds(35)) < 0);
        } finally {
            server.disposeNow();
        }
    }

    private ClaudeProperties properties() {
        ClaudeProperties properties = new ClaudeProperties();
        properties.setApiKey("test-key");
        properties.setRetryDelayMillis(0);
        return properties;
    }

    private ClientResponse response(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header("Content-Type", "application/json")
                .body(body)
                .build();
    }

    private DisposableServer delayedClaudeServer(Duration delay) {
        return HttpServer.create()
                .port(0)
                .route(routes -> routes.post(
                        "/v1/messages",
                        (request, response) -> response
                                .header("Content-Type", "application/json")
                                .sendString(Mono.delay(delay).thenReturn(
                                        "{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}"
                                ))
                ))
                .bindNow();
    }

    private WebClient reactorNettyWebClient(DisposableServer server, Duration responseTimeout) {
        HttpClient httpClient = HttpClient.create().responseTimeout(responseTimeout);
        return WebClient.builder()
                .baseUrl("http://localhost:" + server.port())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
