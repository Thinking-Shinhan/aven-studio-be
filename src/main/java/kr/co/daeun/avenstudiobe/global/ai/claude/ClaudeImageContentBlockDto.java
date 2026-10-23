package kr.co.daeun.avenstudiobe.global.ai.claude;

public record ClaudeImageContentBlockDto(
        String type,
        ClaudeImageSourceDto source
) implements ClaudeContentBlockDto {

    public ClaudeImageContentBlockDto(ClaudeImageSourceDto source) {
        this("image", source);
    }
}
