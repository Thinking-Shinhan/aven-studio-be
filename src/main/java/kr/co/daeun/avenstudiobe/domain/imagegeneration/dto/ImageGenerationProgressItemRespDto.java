package kr.co.daeun.avenstudiobe.domain.imagegeneration.dto;

public record ImageGenerationProgressItemRespDto(
        int outputIndex,
        String status,
        String message
) {
}
