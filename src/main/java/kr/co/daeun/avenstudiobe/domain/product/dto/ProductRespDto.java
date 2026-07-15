package kr.co.daeun.avenstudiobe.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Schema(description = "상품 응답")
public class ProductRespDto {
    @Schema(description = "상품 ID", example = "1")
    private Long id;
    @Schema(description = "상품 코드", example = "AVN-001")
    private String code;
    @Schema(description = "상품명", example = "드라이모션 테일러드 플리츠 스커트 차콜")
    private String name;
    @Schema(description = "상품 카테고리", example = "치마")
    private String category;
    @Schema(description = "상품 성별", example = "F")
    private String gender;
    @Schema(description = "상품 색상", example = "차콜")
    private String color;
    @Schema(description = "상품 가격", example = "129000")
    private int price;
    @Schema(description = "상품 생성 일시", example = "2026-07-08T12:30:00")
    private LocalDateTime createdAt;
    @Schema(description = "상품 앞면과 뒷면 이미지")
    private ProductImagesRespDto productImages;

    public static ProductRespDto from(Product product) {
        return from(product, ProductImagesRespDto.empty());
    }

    public static ProductRespDto from(Product product, ProductImagesRespDto productImages) {
        return new ProductRespDto(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getCategory(),
                product.getGender(),
                product.getColor(),
                product.getPrice(),
                product.getCreatedAt(),
                productImages
        );
    }
}
