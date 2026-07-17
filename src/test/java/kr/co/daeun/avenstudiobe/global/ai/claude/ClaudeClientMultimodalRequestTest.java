package kr.co.daeun.avenstudiobe.global.ai.claude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeClient.ClaudeTextResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

class ClaudeClientMultimodalRequestTest {

    @Test
    void sendsSnakeCaseJsonThroughTheRealReactorNettyConnector() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        DisposableServer server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> request.receive()
                        .aggregate()
                        .asString()
                        .flatMap(body -> {
                            requestBody.set(body);
                            return Mono.delay(Duration.ofMillis(1200))
                                    .then(response
                                            .header("Content-Type", "application/json")
                                            .header("request-id", "req-multimodal-test")
                                            .sendString(Mono.just("""
                                                    {"content":[{"type":"text","text":"ok"}],"stop_reason":"max_tokens"}
                                                    """))
                                            .then());
                        }))
                .bindNow();

        try {
            HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(1));
            WebClient webClient = WebClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.port())
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();
            ClaudeProperties properties = new ClaudeProperties();
            properties.setApiKey("test-key");
            properties.setModel("test-model");
            properties.setMaxRetries(0);
            ClaudeClient client = new ClaudeClient(properties, webClient);
            ClaudeTextResponse result = client.requestMultimodalTextWithMetadata(
                    "system",
                    List.of(
                            new ClaudeTextContentBlockDto("[image assetId=25]"),
                            new ClaudeImageContentBlockDto(
                                    new ClaudeImageSourceDto("image/jpeg", "encoded-jpeg")
                            )
                    ),
                    8192,
                    3
            );

            assertEquals("ok", result.text());
            assertEquals("max_tokens", result.stopReason());
            assertEquals("req-multimodal-test", result.requestId());

            JsonNode json = new ObjectMapper().readTree(requestBody.get());
            assertEquals(8192, json.path("max_tokens").asInt());
            assertFalse(json.has("maxTokens"));
            JsonNode source = json.path("messages").get(0).path("content").get(1).path("source");
            assertEquals("image/jpeg", source.path("media_type").asText());
            assertFalse(source.has("mediaType"));
        } finally {
            server.disposeNow();
        }
    }
}
