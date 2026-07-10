package kr.co.daeun.avenstudiobe.global.ai.claude;

import java.util.List;

public record ClaudeMessageRespDto(
        List<ClaudeContentDto> content
) {
}
