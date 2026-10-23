package kr.co.daeun.avenstudiobe.domain.imagegeneration.dto;

import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ImageGenerationJob;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    private List<ImageGenerationProgressItemRespDto> progressItems;
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
                progressItems(job, results),
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
        if (outputCount == 0) {
            return 15;
        }
        return Math.min(100, (int) Math.round(outputCount * 100.0 / job.getRequestedCount()));
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
            case "PROCESSING" -> "선택한 옵션을 반영해 이미지를 만들고 있어요.";
            case "SUCCEEDED" -> "이미지 생성이 완료되었습니다.";
            case "FAILED" -> failureMessage(job);
            default -> "이미지 생성 상태를 확인하고 있습니다.";
        };
    }

    private static String failureMessage(ImageGenerationJob job) {
        String errorMessage = job.getErrorMessage();
        if (errorMessage == null || errorMessage.isBlank()) {
            return "이미지 생성 중 오류가 발생했습니다.";
        }
        return errorMessage;
    }

    private static List<ImageGenerationProgressItemRespDto> progressItems(
            ImageGenerationJob job,
            List<ImageGenerationResultRespDto> results
    ) {
        int requestedCount = job.getRequestedCount() == null ? 0 : job.getRequestedCount();
        Set<Integer> completedOutputIndexes = results.stream()
                .filter(result -> "GENERATED".equals(result.getStatus())
                        || "GENERATED_NOT_SAVED".equals(result.getStatus()))
                .map(ImageGenerationResultRespDto::getOutputIndex)
                .collect(Collectors.toSet());

        return IntStream.range(0, requestedCount)
                .mapToObj(outputIndex -> progressItem(job, completedOutputIndexes, outputIndex))
                .toList();
    }

    private static ImageGenerationProgressItemRespDto progressItem(
            ImageGenerationJob job,
            Set<Integer> completedOutputIndexes,
            int outputIndex
    ) {
        int imageNumber = outputIndex + 1;
        if (completedOutputIndexes.contains(outputIndex) || "SUCCEEDED".equals(job.getStatus())) {
            return new ImageGenerationProgressItemRespDto(
                    outputIndex,
                    "COMPLETED",
                    String.format("이미지 %d장 생성 완료", imageNumber)
            );
        }
        if ("FAILED".equals(job.getStatus())) {
            return new ImageGenerationProgressItemRespDto(
                    outputIndex,
                    "FAILED",
                    String.format("이미지 %d장 생성 실패: %s", imageNumber, failureMessage(job))
            );
        }
        return new ImageGenerationProgressItemRespDto(outputIndex, "PENDING", null);
    }
}
