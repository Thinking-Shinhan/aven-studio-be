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
                results
        );
    }
}
