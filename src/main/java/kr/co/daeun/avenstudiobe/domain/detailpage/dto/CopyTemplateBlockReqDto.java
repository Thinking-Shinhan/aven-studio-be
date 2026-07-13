package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class CopyTemplateBlockReqDto {

    @NotNull
    private Integer blockOrder;

    @NotBlank
    private String instanceKey;

    @NotBlank
    private String templateKey;

    @NotBlank
    @Pattern(regexp = "IMAGE|TEXT|MIXED", message = "templateType must be one of IMAGE, TEXT, MIXED.")
    private String templateType;

    @Size(max = 30)
    private List<@Valid CopyImageSlotReqDto> imageSlots = List.of();

    @Size(max = 30)
    private List<@Valid TextSlotReqDto> textSlots = List.of();
}
