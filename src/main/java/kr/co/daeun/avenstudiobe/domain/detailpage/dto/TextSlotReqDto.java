package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TextSlotReqDto {

    @NotBlank
    private String slotKey;

    @NotNull
    private Integer slotOrder;

    @NotBlank
    @Pattern(regexp = "EYEBROW|HEADLINE|SUBHEADLINE|BODY|CAPTION",
            message = "textRole must be one of EYEBROW, HEADLINE, SUBHEADLINE, BODY, CAPTION.")
    private String textRole;

    @NotNull
    @Min(1)
    private Integer recommendedMaxLength;

    private String currentContent;
}
