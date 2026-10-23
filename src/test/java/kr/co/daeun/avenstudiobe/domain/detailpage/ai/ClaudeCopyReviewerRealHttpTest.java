package kr.co.daeun.avenstudiobe.domain.detailpage.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeClient;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

@ExtendWith(OutputCaptureExtension.class)
class ClaudeCopyReviewerRealHttpTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendsReviewMaxTokensAndParsesThirtyIssuesWithoutTruncation() throws Exception {
        List<Map<String, Object>> issues = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("category", "CLAIM_RISK");
            issue.put("instanceKey", "block-" + index);
            issue.put("slotKey", "slot-" + index);
            issue.put("issue", "근거를 확인해요. " + index);
            issue.put("suggestion", "제안 문구 " + index);
            issues.add(issue);
        }

        AtomicReference<String> requestBody = new AtomicReference<>();
        DisposableServer server = server(
                claudeResponse(objectMapper.writeValueAsString(Map.of("issues", issues)), "end_turn"),
                requestBody
        );
        try {
            ClaudeProperties properties = properties(8192);
            ClaudeCopyReviewer reviewer = reviewer(server, properties);

            AiCopyReviewResponseDto response = reviewer.review(null, List.of());

            assertEquals(30, response.issues().size());
            assertEquals("block-29", response.issues().get(29).instanceKey());
            JsonNode requestJson = objectMapper.readTree(requestBody.get());
            assertEquals(8192, requestJson.path("max_tokens").asInt());
            assertTrue(requestJson.path("max_tokens").asInt() != 2048);
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void logsWarningWhenLowReviewTokenLimitProducesMaxTokensStopReason(
            CapturedOutput output
    ) throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        DisposableServer server = server(
                claudeResponse("{\"issues\":[]}", "max_tokens"),
                requestBody
        );
        try {
            ClaudeProperties properties = properties(1);
            ClaudeCopyReviewer reviewer = reviewer(server, properties);

            AiCopyReviewResponseDto response = reviewer.review(null, List.of());

            assertEquals(List.of(), response.issues());
            assertEquals(1, objectMapper.readTree(requestBody.get()).path("max_tokens").asInt());
            assertTrue(output.getOut().contains(
                    "Claude copy review response reached the max_tokens limit."
            ));
        } finally {
            server.disposeNow();
        }
    }

    private ClaudeCopyReviewer reviewer(DisposableServer server, ClaudeProperties properties) {
        HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(20));
        WebClient webClient = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + server.port())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        ClaudeClient client = new ClaudeClient(properties, webClient);
        return new ClaudeCopyReviewer(
                new CopyReviewPromptBuilder(),
                client,
                properties,
                new ObjectMapper()
        );
    }

    private ClaudeProperties properties(int reviewMaxTokens) {
        ClaudeProperties properties = new ClaudeProperties();
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        properties.setMaxRetries(0);
        properties.setReviewMaxTokens(reviewMaxTokens);
        properties.setReviewImageTimeoutSeconds(30);
        return properties;
    }

    private DisposableServer server(String responseBody, AtomicReference<String> requestBody) {
        return HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.post(
                        "/v1/messages",
                        (request, response) -> request.receive()
                                .aggregate()
                                .asString()
                                .flatMap(body -> {
                                    requestBody.set(body);
                                    return response
                                            .header("Content-Type", "application/json; charset=UTF-8")
                                            .sendByteArray(Mono.just(
                                                    responseBody.getBytes(StandardCharsets.UTF_8)
                                            ))
                                            .then();
                                })
                ))
                .bindNow();
    }

    private String claudeResponse(String reviewJson, String stopReason) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "content", List.of(Map.of("type", "text", "text", reviewJson)),
                "stop_reason", stopReason
        ));
    }
}
