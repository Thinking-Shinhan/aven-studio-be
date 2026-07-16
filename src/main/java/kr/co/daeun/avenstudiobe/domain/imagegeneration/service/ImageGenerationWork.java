package kr.co.daeun.avenstudiobe.domain.imagegeneration.service;

import kr.co.daeun.avenstudiobe.integration.google.GoogleAiImageProvider;

import java.util.List;

public record ImageGenerationWork(
        Long jobId,
        Long productId,
        long startedAt,
        List<Task> tasks
) {
    public record Task(
            int outputIndex,
            String prompt,
            List<GoogleAiImageProvider.ReferenceImage> referenceImages,
            String aspectRatio,
            String cameraAngle,
            String framing,
            String finalInstruction,
            String assetRole,
            String description
    ) {
    }
}
