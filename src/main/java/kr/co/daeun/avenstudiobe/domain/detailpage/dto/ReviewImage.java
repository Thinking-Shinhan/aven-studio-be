package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

public record ReviewImage(
        Long assetId,
        String assetRole,
        String description,
        String mediaType,
        String base64Data
) {
}
