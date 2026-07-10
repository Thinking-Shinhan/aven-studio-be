package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import jakarta.validation.Valid;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TemplateBlockReqDto {

    @NotNull
    private Integer blockOrder;
    @NotBlank
    private String instanceKey;
    @NotBlank
    private String templateKey;
    @NotBlank
    @Pattern(regexp = "IMAGE|TEXT|MIXED", message = "templateType must be one of IMAGE, TEXT, MIXED.")
    private String templateType;

    @Valid
    @Size(max = 30)
    private List<ImageSlotReqDto> imageSlots = List.of();
}
