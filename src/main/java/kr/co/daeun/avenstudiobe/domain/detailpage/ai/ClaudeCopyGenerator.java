package kr.co.daeun.avenstudiobe.domain.detailpage.ai;

import java.util.ArrayList;
import java.util.List;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerationContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerationResult;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyResultDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.validator.CopyValidationError;
import kr.co.daeun.avenstudiobe.domain.detailpage.validator.CopyValidationErrorCode;
import kr.co.daeun.avenstudiobe.domain.detailpage.validator.CopyValidationResult;
import kr.co.daeun.avenstudiobe.domain.detailpage.validator.GeneratedCopyValidator;
import kr.co.daeun.avenstudiobe.global.ai.claude.ClaudeClient;
import kr.co.daeun.avenstudiobe.global.exception.InvalidCopyGenerationResultException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ClaudeCopyGenerator implements CopyGenerator {

    private final CopyPromptBuilder copyPromptBuilder;
    private final ClaudeClient claudeClient;
    private final GeneratedCopyValidator generatedCopyValidator;
    private final JsonMapper jsonMapper;

    @Override
    public CopyGenerationResult generate(CopyGenerationContext context) {
        String systemPrompt = copyPromptBuilder.buildSystemPrompt();
        String userMessage = copyPromptBuilder.buildUserMessage(context);
        String rawResponse = claudeClient.requestText(systemPrompt, userMessage);

        ParseResult firstParse = parse(rawResponse);
        if (firstParse.failed()) {
            return correctAndValidate(
                    context,
                    systemPrompt,
                    rawResponse,
                    List.of(firstParse.toValidationError())
            );
        }

        CopyGenerationResult firstResult = firstParse.result();
        CopyValidationResult firstValidation = generatedCopyValidator.validate(context, firstResult);
        if (firstValidation.isValid()) {
            return sortResult(context, firstResult);
        }

        return correctAndValidate(context, systemPrompt, rawResponse, firstValidation.errors());
    }

    private CopyGenerationResult correctAndValidate(
            CopyGenerationContext context,
            String systemPrompt,
            String rawResponse,
            List<CopyValidationError> errors
    ) {
        String correctionMessage = copyPromptBuilder.buildCorrectionMessage(context, rawResponse, errors);
        String correctedRawResponse = claudeClient.requestText(systemPrompt, correctionMessage);
        ParseResult correctedParse = parse(correctedRawResponse);

        if (correctedParse.failed()) {
            throw new InvalidCopyGenerationResultException(
                    "교정 문구 생성 응답 JSON 파싱에 실패했습니다.",
                    correctedParse.exception()
            );
        }

        CopyGenerationResult correctedResult = correctedParse.result();
        CopyValidationResult correctedValidation = generatedCopyValidator.validate(context, correctedResult);

        if (correctedValidation.isValid() || correctedValidation.hasOnlyLengthExceeded()) {
            return sortResult(context, correctedResult);
        }

        throw new InvalidCopyGenerationResultException("교정 문구 생성 결과가 검증을 통과하지 못했습니다.");
    }

    private CopyGenerationResult sortResult(CopyGenerationContext context, CopyGenerationResult result) {
        return new CopyGenerationResult(generatedCopyValidator.sortCopies(context, result.copies()));
    }

    private ParseResult parse(String rawResponse) {
        try {
            return ParseResult.success(parseResult(rawResponse));
        } catch (RuntimeException e) {
            return ParseResult.failure(e);
        }
    }

    private CopyGenerationResult parseResult(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalArgumentException("AI response is empty.");
        }

        String sanitized = sanitizeJsonArray(rawResponse);

        try {
            JsonNode root = jsonMapper.readTree(sanitized);
            if (root == null || !root.isArray()) {
                throw new IllegalArgumentException("AI response must be a JSON array.");
            }

            List<CopyResultDto> copies = new ArrayList<>();
            for (JsonNode item : root) {
                copies.add(new CopyResultDto(
                        textOrNull(item, "instanceKey"),
                        textOrNull(item, "slotKey"),
                        textOrNull(item, "content")
                ));
            }

            return new CopyGenerationResult(copies);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to parse AI copy response.", e);
        }
    }

    private String sanitizeJsonArray(String rawResponse) {
        String sanitized = rawResponse.trim();

        if (sanitized.startsWith("```json")) {
            sanitized = sanitized.substring("```json".length()).trim();
        }

        if (sanitized.startsWith("```")) {
            sanitized = sanitized.substring("```".length()).trim();
        }

        if (sanitized.endsWith("```")) {
            sanitized = sanitized.substring(0, sanitized.length() - 3).trim();
        }

        int start = sanitized.indexOf('[');
        int end = sanitized.lastIndexOf(']');

        if (start >= 0 && end > start) {
            return sanitized.substring(start, end + 1);
        }

        return sanitized;
    }

    private String textOrNull(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }

        return node.path(fieldName).asText();
    }

    private record ParseResult(
            CopyGenerationResult result,
            RuntimeException exception
    ) {
        private static ParseResult success(CopyGenerationResult result) {
            return new ParseResult(result, null);
        }

        private static ParseResult failure(RuntimeException exception) {
            return new ParseResult(null, exception);
        }

        private boolean failed() {
            return exception != null;
        }

        private CopyValidationError toValidationError() {
            return new CopyValidationError(
                    CopyValidationErrorCode.RESPONSE_PARSE_FAILED,
                    null,
                    null,
                    exception.getMessage()
            );
        }
    }
}
