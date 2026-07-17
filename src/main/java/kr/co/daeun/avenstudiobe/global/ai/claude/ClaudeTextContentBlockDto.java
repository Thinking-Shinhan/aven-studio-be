package kr.co.daeun.avenstudiobe.global.ai.claude;

public record ClaudeTextContentBlockDto(
        String type,
        String text
) implements ClaudeContentBlockDto {

    public ClaudeTextContentBlockDto(String text) {
        this("text", text);
    }
}
