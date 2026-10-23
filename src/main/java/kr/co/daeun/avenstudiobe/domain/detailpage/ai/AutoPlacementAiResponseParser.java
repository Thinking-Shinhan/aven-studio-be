package kr.co.daeun.avenstudiobe.domain.detailpage.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class AutoPlacementAiResponseParser {

    private final JsonMapper jsonMapper;

    public AutoPlacementAiResponseDto parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new AiResponseParseException("AI response is empty.");
        }

        String sanitized = sanitizeJson(rawText);

        try {
            return jsonMapper.readValue(sanitized, AutoPlacementAiResponseDto.class);
        } catch (JacksonException e) {
            throw new AiResponseParseException("Failed to parse AI placement response.", e);
        }
    }

    String sanitizeJson(String rawText) {
        String sanitized = rawText.trim();

        if (sanitized.startsWith("```json")) {
            sanitized = sanitized.substring("```json".length()).trim();
        }

        if (sanitized.startsWith("```")) {
            sanitized = sanitized.substring("```".length()).trim();
        }

        if (sanitized.endsWith("```")) {
            sanitized = sanitized.substring(0, sanitized.length() - 3).trim();
        }

        int start = sanitized.indexOf('{');
        int end = sanitized.lastIndexOf('}');

        if (start >= 0 && end > start) {
            sanitized = sanitized.substring(start, end + 1);
        }

        return sanitized;
    }
}