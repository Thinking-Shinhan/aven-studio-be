package kr.co.daeun.avenstudiobe.domain.library.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ImageFolderRespDto(
        Long productId,
        String productCode,
        String productName,
        String category,
        List<LibraryPreviewImageRespDto> previewImages,
        LibraryAssetCountRespDto counts,
        LocalDateTime updatedAt
) {
}