package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import java.util.List;

public record AutoPlacementContext(
        Long productId,
        ProductPlacementInfo productInfo,
        List<PlacementSlot> slots,
        List<AssetPlacementCandidate> assets
) {
}
