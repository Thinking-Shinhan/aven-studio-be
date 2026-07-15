package kr.co.daeun.avenstudiobe.domain.imagegeneration.dto;

import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ImageGenerationJob;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ImageGenerationJobRespDto {

    private Long jobId;
    private String status;
    private String cutType;
    private String generationMode;
    private String aiName;
    private int requestedCount;
    private int outputCount;
    private Integer latencyMs;
    private String errorMessage;
    private int progressPercent;
    private String progressStage;
    private String progressMessage;
    private List<ImageGenerationResultRespDto> results;

    public static ImageGenerationJobRespDto from(
            ImageGenerationJob job,
            List<ImageGenerationResultRespDto> results
    ) {
        return new ImageGenerationJobRespDto(
                job.getId(),
                job.getStatus(),
                job.getCutType(),
                job.getGenerationMode(),
                job.getAiName(),
                job.getRequestedCount(),
                job.getOutputCount(),
                job.getLatencyMs(),
                job.getErrorMessage(),
                progressPercent(job),
                progressStage(job),
                progressMessage(job),
                results
        );
    }

    private static int progressPercent(ImageGenerationJob job) {
        return switch (job.getStatus()) {
            case "PENDING" -> 5;
            case "PROCESSING" -> processingPercent(job);
            case "SUCCEEDED" -> 100;
            case "FAILED" -> processingPercent(job);
            default -> 0;
        };
    }

    private static int processingPercent(ImageGenerationJob job) {
        if (job.getRequestedCount() == null || job.getRequestedCount() <= 0) {
            return 15;
        }
        int outputCount = job.getOutputCount() == null ? 0 : job.getOutputCount();
        return Math.min(95, 15 + (int) Math.round(outputCount * 80.0 / job.getRequestedCount()));
    }

    private static String progressStage(ImageGenerationJob job) {
        return switch (job.getStatus()) {
            case "PENDING" -> "QUEUED";
            case "PROCESSING" -> "GENERATING";
            case "SUCCEEDED" -> "COMPLETED";
            case "FAILED" -> "FAILED";
            default -> "UNKNOWN";
        };
    }

    private static String progressMessage(ImageGenerationJob job) {
        return switch (job.getStatus()) {
            case "PENDING" -> "이미지 생성 작업을 준비하고 있습니다.";
            case "PROCESSING" -> String.format(
                    "%d장 중 %d장이 생성되었습니다.",
                    job.getRequestedCount(),
                    job.getOutputCount()
            );
            case "SUCCEEDED" -> "이미지 생성이 완료되었습니다.";
            case "FAILED" -> "이미지 생성 중 오류가 발생했습니다.";
            default -> "이미지 생성 상태를 확인하고 있습니다.";
        };
    }
}
