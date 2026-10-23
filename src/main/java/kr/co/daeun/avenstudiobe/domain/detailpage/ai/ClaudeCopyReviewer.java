package kr.co.daeun.avenstudiobe.domain.detailpage.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewImage;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeClient;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeClient.ClaudeTextResponse;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeOperation;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeCopyReviewer {

    private static final Pattern JSON_CODE_FENCE = Pattern.compile(
            "```(?:json)?\\s*(.*?)\\s*```",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final CopyReviewPromptBuilder promptBuilder;
    private final ClaudeClient claudeClient;
    private final ClaudeProperties claudeProperties;
    private final ObjectMapper objectMapper;

    public AiCopyReviewResponseDto review(
            CopyReviewContext context,
            List<ReviewImage> images
    ) {
        ClaudeTextResponse response = claudeClient.requestMultimodalTextWithMetadata(
                ClaudeOperation.COPY_REVIEW,
                promptBuilder.buildSystemPrompt(),
                promptBuilder.buildUserContent(context, images),
                claudeProperties.getReviewMaxTokens(),
                claudeProperties.getReviewImageTimeoutSeconds()
        );

        warnIfMaxTokens(response.stopReason());
        try {
            return parse(response.text());
        } catch (IllegalStateException e) {
            log.warn(
                    "Claude copy review response parsing failed. stopReason={}, responseLength={}, requestId={}",
                    response.stopReason(),
                    response.text() == null ? 0 : response.text().length(),
                    response.requestId()
            );
            throw e;
        }
    }

    private AiCopyReviewResponseDto parse(String rawResponse) {
        JsonNode json = extractReviewJsonObject(rawResponse);
        try {
            return objectMapper.treeToValue(json, AiCopyReviewResponseDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AI 문구 검수 응답 JSON 파싱에 실패했습니다.", e);
        }
    }

    private JsonNode extractReviewJsonObject(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("AI 문구 검수 응답이 비어 있습니다.");
        }

        String trimmed = text.trim();
        List<JsonNode> fencedCandidates = new ArrayList<>();
        Matcher matcher = JSON_CODE_FENCE.matcher(trimmed);
        while (matcher.find()) {
            addReviewCandidate(fencedCandidates, matcher.group(1));
        }
        if (!fencedCandidates.isEmpty()) {
            return requireSingleCandidate(fencedCandidates);
        }

        List<JsonNode> candidates = new ArrayList<>();
        for (String candidate : findBalancedJsonObjects(trimmed)) {
            addReviewCandidate(candidates, candidate);
        }
        return requireSingleCandidate(candidates);
    }

    private List<String> findBalancedJsonObjects(String text) {
        List<String> candidates = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;

        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"' && depth > 0) {
                inString = true;
            } else if (current == '{') {
                if (depth == 0) {
                    start = index;
                }
                depth++;
            } else if (current == '}' && depth > 0) {
                depth--;
                if (depth == 0 && start >= 0) {
                    candidates.add(text.substring(start, index + 1));
                    start = -1;
                }
            }
        }
        return candidates;
    }

    private void addReviewCandidate(List<JsonNode> candidates, String candidate) {
        try (JsonParser parser = objectMapper.createParser(candidate)) {
            JsonNode node = objectMapper.readTree(parser);
            if (node != null && node.isObject() && node.has("issues") && parser.nextToken() == null) {
                candidates.add(node);
            }
        } catch (JsonProcessingException ignored) {
            // 설명에 포함된 중괄호나 유효하지 않은 JSON 후보는 건너뜁니다.
        } catch (java.io.IOException ignored) {
            // String 기반 파서는 일반적으로 JsonProcessingException을 사용하지만 안전하게 무시합니다.
        }
    }

    private JsonNode requireSingleCandidate(List<JsonNode> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalStateException("AI 문구 검수 응답에서 JSON 객체를 찾을 수 없습니다.");
        }
        if (candidates.size() > 1) {
            throw new IllegalStateException("AI 문구 검수 응답에 JSON 객체가 여러 개 포함되어 있습니다.");
        }
        return candidates.get(0);
    }

    private void warnIfMaxTokens(String stopReason) {
        if ("max_tokens".equals(stopReason)) {
            log.warn("Claude copy review response reached the max_tokens limit.");
        }
    }
}
