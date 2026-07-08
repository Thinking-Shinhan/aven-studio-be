package kr.co.daeun.avenstudiobe.domain.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.daeun.avenstudiobe.domain.product.dto.ProductListRespDto;
import kr.co.daeun.avenstudiobe.domain.product.dto.ProductRespDto;
import kr.co.daeun.avenstudiobe.domain.product.service.ProductService;
import kr.co.daeun.avenstudiobe.global.exception.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
@Tag(name = "Products", description = "상품 검색 및 목록 조회 API")
@SecurityRequirement(name = "accessTokenCookie")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "상품 목록 조회", description = "카테고리, 색상, 성별, 키워드 조건으로 필터링된 상품 목록을 페이지 형태로 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상품 목록 조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "필터 조건이 올바르지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<ProductListRespDto> getProductList(
            @Parameter(description = "상품 카테고리 필터", example = "하의")
            @RequestParam(required = false) String category,
            @Parameter(description = "상품 색상 필터", example = "브라운")
            @RequestParam(required = false) String color,
            @Parameter(description = "상품 성별 필터", example = "F")
            @RequestParam(required = false) String gender,
            @Parameter(description = "상품명 검색 키워드", example = "드라이")
            @RequestParam(required = false) String keyword,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
            ){

        ProductListRespDto products = productService.getProductList(category, color, gender, keyword, pageable);

        return ResponseEntity.ok().body(products);
    }

}
