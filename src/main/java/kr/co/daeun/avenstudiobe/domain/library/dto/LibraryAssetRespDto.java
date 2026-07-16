package kr.co.daeun.avenstudiobe.domain.library.dto;

import java.time.LocalDateTime;

public record LibraryAssetRespDto(
        Long assetId,
        String type,
        String assetRole,
        String imageUrl,
        String description,
        String status,
        Boolean isLiked,
        LocalDateTime createdAt
) {
}
