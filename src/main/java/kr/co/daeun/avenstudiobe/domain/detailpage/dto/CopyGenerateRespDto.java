package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CopyGenerateRespDto {

    private Long productId;
    private List<CopyResultDto> copies;
    private List<CopyWarningDto> warnings;

    public static CopyGenerateRespDto empty(Long productId) {
        return new CopyGenerateRespDto(productId, List.of(), List.of());
    }
}
