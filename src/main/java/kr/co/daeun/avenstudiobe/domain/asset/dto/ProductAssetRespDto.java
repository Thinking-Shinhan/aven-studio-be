package kr.co.daeun.avenstudiobe.domain.asset.dto;

public record ProductAssetRespDto(
        Long assetId,
        String imageUrl,
        String objectName,
        String originalFilename,
        String contentType,
        long size
) {
}
