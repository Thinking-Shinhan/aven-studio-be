package kr.co.daeun.avenstudiobe.domain.library.dto;

public record ImageFolderRespDto(
        Long productId,
        String productCode,
        String productName,
        String category,
        long assetCount,
        Long thumbnailAssetId,
        String thumbnailUrl
) {
}
