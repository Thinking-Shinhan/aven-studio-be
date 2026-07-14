package kr.co.daeun.avenstudiobe.global.ai.claude;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
}
