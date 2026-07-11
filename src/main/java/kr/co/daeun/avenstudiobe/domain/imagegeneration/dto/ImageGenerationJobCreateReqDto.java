package kr.co.daeun.avenstudiobe.domain.imagegeneration.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tools.jackson.databind.JsonNode;

@Data
public class ImageGenerationJobCreateReqDto {

    @NotNull
    private Long productId;

    @NotBlank
    private String cutType;

    private String generationMode = "PARALLEL";

    @NotBlank
    private String prompt;

    private String negativePrompt;

    private JsonNode userOptionsJson;

    private JsonNode referenceJson;

    @Min(1)
    @Max(4)
    private Integer requestedCount;
}
