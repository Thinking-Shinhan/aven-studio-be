package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

public record ReviewAssetSource(
        Long assetId,
        String objectName,
        String contentType,
        String assetRole,
        String description
) {
}
