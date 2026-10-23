package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AutoImagePlacementRespDto {

    private Long productId;
    private List<ImagePlacementRespDto> placements;

    public static AutoImagePlacementRespDto empty(Long productId) {
        return new AutoImagePlacementRespDto(productId, List.of());
    }
}
