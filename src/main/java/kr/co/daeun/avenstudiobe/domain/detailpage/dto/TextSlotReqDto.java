package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TextSlotReqDto {

    public static final String TEXT_ROLE_REGEXP = "EYEBROW|HEADLINE|SUBHEADLINE|BODY|CAPTION";
    public static final String TEXT_ROLE_MESSAGE =
            "textRole must be one of EYEBROW, HEADLINE, SUBHEADLINE, BODY, CAPTION.";

    @NotBlank
    private String slotKey;

    @NotNull
    private Integer slotOrder;

    @NotBlank
    @Pattern(regexp = TEXT_ROLE_REGEXP, message = TEXT_ROLE_MESSAGE)
    private String textRole;

    @NotNull
    @Min(1)
    private Integer recommendedMaxLength;

    private String currentContent;
}
