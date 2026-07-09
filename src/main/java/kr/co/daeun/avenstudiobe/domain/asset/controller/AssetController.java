package kr.co.daeun.avenstudiobe.domain.asset.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.daeun.avenstudiobe.domain.asset.dto.ProductAssetRespDto;
import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import kr.co.daeun.avenstudiobe.domain.asset.service.AssetService;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import kr.co.daeun.avenstudiobe.domain.user.service.UsersService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/assets")
@Tag(name = "Assets", description = "상품 이미지 업로드 및 조회 API")
@SecurityRequirement(name = "accessTokenCookie")
public class AssetController {

    private final AssetService assetService;
    private final ProductRepository productRepository;
    private final UsersService usersService;

    @PostMapping(value = "/products/{productId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "상품 이미지 업로드", description = "모델컷/제품컷 생성에 사용할 상품 앞면, 뒷면, 코디 이미지를 업로드합니다.")
    public ResponseEntity<List<ProductAssetRespDto>> uploadProductImages(
            @PathVariable Long productId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("roles") List<String> roles,
            Authentication authentication
    ) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("업로드할 이미지 파일을 선택해주세요.");
        }

        Product product = getAccessibleProduct(productId, authentication);
        List<Asset> assets = assetService.uploadProductImages(files, roles, product);

        List<ProductAssetRespDto> response = assets.stream()
                .map(asset -> new ProductAssetRespDto(
                        asset.getId(),
                        asset.getAssetRole(),
                        asset.getFileUrl(),
                        asset.getObjectName(),
                        asset.getOriginalFilename(),
                        asset.getContentType(),
                        asset.getSize()
                ))
                .toList();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/products/{productId}/images")
    @Operation(summary = "상품 이미지 조회", description = "상품에 연결된 업로드 이미지와 생성 이미지를 조회합니다.")
    public ResponseEntity<List<ProductAssetRespDto>> getProductImages(
            @PathVariable Long productId,
            Authentication authentication
    ) {
        getAccessibleProduct(productId, authentication);

        List<ProductAssetRespDto> response = assetService.getProductImages(productId).stream()
                .map(asset -> new ProductAssetRespDto(
                        asset.assetId(),
                        asset.assetRole(),
                        asset.imageUrl(),
                        asset.objectName(),
                        asset.originalFilename(),
                        asset.contentType(),
                        asset.size()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{assetId}")
    @Operation(summary = "상품 이미지 삭제", description = "상품 이미지 또는 생성 이미지를 삭제합니다.")
    public ResponseEntity<Void> deleteAsset(
            @PathVariable Long assetId,
            Authentication authentication
    ) {
        User user = usersService.findByLoginId(authentication.getName());
        assetService.deleteAssetByIdAndBrandId(assetId, user.getBrand().getId());
        return ResponseEntity.noContent().build();
    }

    private Product getAccessibleProduct(Long productId, Authentication authentication) {
        User user = usersService.findByLoginId(authentication.getName());

        return productRepository.findByIdAndBrand_Id(productId, user.getBrand().getId())
                .orElseThrow(() -> new IllegalArgumentException("상품이 존재하지 않거나 접근 권한이 없습니다."));
    }
}
