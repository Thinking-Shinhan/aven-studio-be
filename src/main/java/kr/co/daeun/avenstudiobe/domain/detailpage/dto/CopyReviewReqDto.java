package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CopyReviewReqDto(
        @NotNull
        Long productId,

        @NotNull
        @Size(min = 1, max = 50)
        List<@Valid ReviewTemplateBlockReqDto> templateBlocks
) {

    public record ReviewTemplateBlockReqDto(
            @NotNull
            Integer blockOrder,

            @NotBlank
            String instanceKey,

            @NotBlank
            String templateKey,

            @NotBlank
            @Pattern(
                    regexp = CopyTemplateBlockReqDto.TEMPLATE_TYPE_REGEXP,
                    message = CopyTemplateBlockReqDto.TEMPLATE_TYPE_MESSAGE
            )
            String templateType,

            @Size(max = 30)
            List<@Valid ReviewImageSlotReqDto> imageSlots,

            @Size(max = 30)
            List<@Valid ReviewTextSlotReqDto> textSlots
    ) {
    }

    public record ReviewImageSlotReqDto(
            @NotBlank
            String slotKey,

            @NotNull
            Integer slotOrder,

            Long assetId
    ) {
    }

    public record ReviewTextSlotReqDto(
            @NotBlank
            String slotKey,

            @NotNull
            Integer slotOrder,

            @NotBlank
            @Pattern(
                    regexp = TextSlotReqDto.TEXT_ROLE_REGEXP,
                    message = TextSlotReqDto.TEXT_ROLE_MESSAGE
            )
            String textRole,

            @NotNull
            @Min(1)
            Integer recommendedMaxLength,

            String currentContent
    ) {
    }
}
