package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AutoImagePlacementReqDto {

    @NotNull
    private Long productId;

    @Valid
    @Size(max = 50, message = "템플릿 블록은 최대 50개까지 허용됩니다.")
    private List<TemplateBlockReqDto> templateBlocks = List.of();
}
