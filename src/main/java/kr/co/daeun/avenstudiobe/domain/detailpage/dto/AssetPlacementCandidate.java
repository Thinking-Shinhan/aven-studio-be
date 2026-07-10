package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

public record AssetPlacementCandidate(
        Long assetId,
        String assetRole,
        String description,
        String fileUrl
) {
}
