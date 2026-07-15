package kr.co.daeun.avenstudiobe.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "상품 앞면 또는 뒷면 이미지")
public record ProductImageRespDto(
        @Schema(description = "에셋 ID", example = "15")
        Long assetId,
        @Schema(description = "GCS 서명 URL")
        String imageUrl
) {
}
