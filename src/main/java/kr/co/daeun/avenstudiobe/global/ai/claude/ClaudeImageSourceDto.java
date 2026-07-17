package kr.co.daeun.avenstudiobe.global.ai.claude;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClaudeImageSourceDto(
        String type,

        @JsonProperty("media_type")
        String mediaType,

        String data
) {

    public ClaudeImageSourceDto(String mediaType, String data) {
        this("base64", mediaType, data);
    }
}
