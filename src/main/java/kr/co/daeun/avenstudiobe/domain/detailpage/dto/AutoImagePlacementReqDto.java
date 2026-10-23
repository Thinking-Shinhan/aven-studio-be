package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class AutoImagePlacementReqDto {

    @NotNull(message = "상품 ID는 필수입니다.")
    private Long productId;

    @Size(max = 50, message = "템플릿 블록은 최대 50개까지 허용합니다.")
    private List<@Valid TemplateBlockReqDto> templateBlocks = List.of();
}
