package kr.co.daeun.avenstudiobe.domain.imagegeneration.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ImageGenerationJobCreateReqDto {

    @NotNull
    private Long productId;

    @NotNull
    private Long brandMoodId;

    @NotBlank
    private String cutType;

    private String generationMode = "PARALLEL";

    @NotBlank
    private String prompt;

    private String negativePrompt;

    private String userOptionsJson = "{}";

    private String referenceJson = "{}";

    @Min(1)
    @Max(4)
    private int requestedCount = 2;
}
