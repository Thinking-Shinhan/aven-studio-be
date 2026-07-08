package kr.co.daeun.avenstudiobe.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.co.daeun.avenstudiobe.global.common.dto.PageRespDto;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Schema(description = "페이지 상품 목록 응답")
public class ProductListRespDto {
    @Schema(description = "적용된 카테고리 필터", example = "하의")
    private String category;
    @Schema(description = "적용된 색상 필터", example = "브라운")
    private String color;
    @Schema(description = "적용된 성별 필터", example = "F")
    private String gender;
    @Schema(description = "적용된 키워드 필터", example = "드라이")
    private String keyword;
    @Schema(description = "페이지 처리된 상품 목록")
    private PageRespDto<ProductRespDto> products;
}
