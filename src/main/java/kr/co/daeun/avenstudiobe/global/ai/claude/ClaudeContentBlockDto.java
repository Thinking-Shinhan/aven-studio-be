package kr.co.daeun.avenstudiobe.global.ai.claude;

public sealed interface ClaudeContentBlockDto
        permits ClaudeTextContentBlockDto, ClaudeImageContentBlockDto {

    String type();
}
