package kr.co.daeun.avenstudiobe.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "상품 앞면과 뒷면 이미지")
public record ProductImagesRespDto(
        @Schema(description = "상품 앞면 이미지. 등록되지 않은 경우 null")
        ProductImageRespDto front,
        @Schema(description = "상품 뒷면 이미지. 등록되지 않은 경우 null")
        ProductImageRespDto back
) {
    public static ProductImagesRespDto empty() {
        return new ProductImagesRespDto(null, null);
    }
}
