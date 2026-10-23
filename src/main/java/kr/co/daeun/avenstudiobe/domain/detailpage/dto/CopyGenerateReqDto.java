package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class CopyGenerateReqDto {

    @NotNull
    private Long productId;

    @NotNull
    @Size(min = 1, max = 50)
    private List<@Valid CopyTemplateBlockReqDto> templateBlocks = List.of();
}
