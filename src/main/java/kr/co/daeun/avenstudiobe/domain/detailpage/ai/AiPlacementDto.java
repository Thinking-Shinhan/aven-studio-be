package kr.co.daeun.avenstudiobe.domain.detailpage.ai;

public record AiPlacementDto(
        String instanceKey,
        String slotKey,
        Long assetId,
        String reason
) {
}
