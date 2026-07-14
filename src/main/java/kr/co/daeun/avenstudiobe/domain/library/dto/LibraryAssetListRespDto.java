package kr.co.daeun.avenstudiobe.domain.library.dto;

import java.util.List;

public record LibraryAssetListRespDto(
        Long productId,
        String productCode,
        String productName,
        String category,
        List<LibraryAssetRespDto> assets
) {
}
