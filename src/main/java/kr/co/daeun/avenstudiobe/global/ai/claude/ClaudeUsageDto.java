package kr.co.daeun.avenstudiobe.global.ai.claude;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClaudeUsageDto(
        @JsonProperty("input_tokens")
        Long inputTokens,

        @JsonProperty("output_tokens")
        Long outputTokens,

        @JsonProperty("cache_creation_input_tokens")
        Long cacheCreationInputTokens,

        @JsonProperty("cache_read_input_tokens")
        Long cacheReadInputTokens
) {
}
