package kr.co.daeun.avenstudiobe.global.ai.claude;

import java.util.List;

public record ClaudeMultimodalMessageDto(
        String role,
        List<ClaudeContentBlockDto> content
) {
}
