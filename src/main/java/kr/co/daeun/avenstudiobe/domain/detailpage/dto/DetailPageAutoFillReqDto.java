package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class DetailPageAutoFillReqDto {

    @NotNull(message = "상품 ID는 필수입니다.")
    private Long productId;

    private DetailPageAutoFillMode mode = DetailPageAutoFillMode.REGENERATE_ALL;

    private String targetInstanceKey;

    @Size(max = 50, message = "템플릿 블록은 최대 50개까지 허용됩니다.")
    private List<@Valid DetailPageAutoFillTemplateBlockReqDto> templateBlocks = List.of();

    public DetailPageAutoFillMode getMode() {
        return mode == null ? DetailPageAutoFillMode.REGENERATE_ALL : mode;
    }
}
