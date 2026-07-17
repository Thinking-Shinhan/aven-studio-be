package kr.co.daeun.avenstudiobe.global.ai.claude;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ClaudeMessageRespDto(
        List<ClaudeContentDto> content,

        @JsonProperty("stop_reason")
        String stopReason
) {
}
