package kr.co.daeun.avenstudiobe.global.ai.claude;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClaudeMessageReqDto(
        String model,

        @JsonProperty("max_tokens")
        Integer maxTokens,

        Double temperature,

        String system,

        List<ClaudeMessageDto> messages
) {
}
