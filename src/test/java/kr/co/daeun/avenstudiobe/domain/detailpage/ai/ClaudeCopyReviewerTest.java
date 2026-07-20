package kr.co.daeun.avenstudiobe.domain.detailpage.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeClient;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeClient.ClaudeTextResponse;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeOperation;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ClaudeCopyReviewerTest {

    private final CopyReviewPromptBuilder promptBuilder = mock(CopyReviewPromptBuilder.class);
    private final ClaudeClient claudeClient = mock(ClaudeClient.class);
    private final ClaudeProperties properties = properties();
    private final ClaudeCopyReviewer reviewer = new ClaudeCopyReviewer(
            promptBuilder,
            claudeClient,
            properties,
            new ObjectMapper()
    );

    @Test
    void callsClaudeWithReviewLimitsSanitizesAndParsesTheResponse() {
        when(promptBuilder.buildSystemPrompt()).thenReturn("system");
        when(promptBuilder.buildUserContent(null, List.of())).thenReturn(List.of());
        when(claudeClient.requestMultimodalTextWithMetadata(
                eq(ClaudeOperation.COPY_REVIEW),
                eq("system"),
                anyList(),
                eq(8192),
                eq(30)
        )).thenReturn(new ClaudeTextResponse(
                """
                    설명 앞부분 {참고용 중괄호}
                    ```json
                    {"issues":[{"category":"CLAIM_RISK","instanceKey":"block-1","slotKey":"copy-1","issue":"근거가 부족해요.","suggestion":"표현을 완화해요.","extraIssueField":"ignored"}],"extraResponseField":true}
                    ```
                    설명 뒷부분 {이것도 JSON 아님}
                    """,
                "max_tokens",
                "req-success"
        ));

        AiCopyReviewResponseDto response = reviewer.review(null, List.of());

        assertEquals(1, response.issues().size());
        assertEquals("CLAIM_RISK", response.issues().get(0).category());
        assertEquals("block-1", response.issues().get(0).instanceKey());
        assertEquals("copy-1", response.issues().get(0).slotKey());
    }

    @Test
    void raisesParsingFailureWithoutCreatingFallbackIssuesAndLogsSafeMetadata(CapturedOutput output) {
        when(promptBuilder.buildSystemPrompt()).thenReturn("system");
        when(promptBuilder.buildUserContent(null, List.of())).thenReturn(List.of());
        when(claudeClient.requestMultimodalTextWithMetadata(
                eq(ClaudeOperation.COPY_REVIEW),
                anyString(),
                anyList(),
                anyInt(),
                anyInt()
        )).thenReturn(new ClaudeTextResponse("not-json-secret", "end_turn", "req-parse-failure"));

        assertThrows(IllegalStateException.class, () -> reviewer.review(null, List.of()));
        assertTrue(output.getOut().contains("stopReason=end_turn"));
        assertTrue(output.getOut().contains("responseLength=15"));
        assertTrue(output.getOut().contains("requestId=req-parse-failure"));
        assertFalse(output.getOut().contains("not-json-secret"));
    }

    @Test
    void rejectsAmbiguousMultipleReviewObjects() {
        when(promptBuilder.buildSystemPrompt()).thenReturn("system");
        when(promptBuilder.buildUserContent(null, List.of())).thenReturn(List.of());
        when(claudeClient.requestMultimodalTextWithMetadata(
                eq(ClaudeOperation.COPY_REVIEW),
                anyString(),
                anyList(),
                anyInt(),
                anyInt()
        )).thenReturn(new ClaudeTextResponse(
                "{\"issues\":[]} 사이 텍스트 {\"issues\":[]}",
                "end_turn",
                "req-ambiguous"
        ));

        assertThrows(IllegalStateException.class, () -> reviewer.review(null, List.of()));
    }

    private ClaudeProperties properties() {
        ClaudeProperties properties = new ClaudeProperties();
        properties.setReviewMaxTokens(8192);
        properties.setReviewImageTimeoutSeconds(30);
        return properties;
    }
}
