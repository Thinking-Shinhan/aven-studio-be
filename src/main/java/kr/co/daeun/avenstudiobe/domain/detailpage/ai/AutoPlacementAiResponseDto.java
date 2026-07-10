package kr.co.daeun.avenstudiobe.domain.detailpage.ai;

import java.util.List;

public record AutoPlacementAiResponseDto(
        List<AiPlacementDto> placements
) {
    public AutoPlacementAiResponseDto {
        placements = placements == null ? List.of() : placements;
    }
}
