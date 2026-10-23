package kr.co.daeun.avenstudiobe.domain.asset.controller;

import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import kr.co.daeun.avenstudiobe.domain.asset.service.AssetService;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/test/assets")
public class AssetTestController {

    private final AssetService assetService;
    private final ProductRepository productRepository;

    @PostMapping(value = "/products/{productId}/full", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAssetWithAllColumns(
            @PathVariable Long productId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type,
            @RequestParam("assetRole") String assetRole,
            @RequestParam("prompt") String prompt,
            @RequestParam("description") String description,
            @RequestParam("status") String status
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product does not exist."));

        Asset asset = assetService.uploadTestAssetWithMetadata(
                file,
                product,
                type,
                assetRole,
                prompt,
                description,
                status
        );

        return ResponseEntity.ok(toFullAssetResponse(asset, productId));
    }

    @PostMapping("/{productId}")
    public ResponseEntity<?> uploadImages(
            @PathVariable Long productId,
            @RequestParam("files") List<MultipartFile> files
    ) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("상품이 존재하지 않습니다."));

        List<Asset> assets = assetService.uploadProductImages(files, product);

        List<Map<String, Object>> response = assets.stream()
                .map(asset -> Map.<String, Object>of(
                        "assetId", asset.getId(),
                        "objectName", asset.getObjectName(),
                        "originalFilename", asset.getOriginalFilename(),
                        "contentType", asset.getContentType(),
                        "size", asset.getSize()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{assetId}")
    public ResponseEntity<Void> deleteAsset(@PathVariable Long assetId) {
        assetService.deleteAssetById(assetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<?> getProductImages(@PathVariable Long productId) {
        return ResponseEntity.ok(assetService.getProductImages(productId));
    }

    private Map<String, Object> toFullAssetResponse(Asset asset, Long productId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("assetId", asset.getId());
        response.put("type", asset.getType());
        response.put("assetRole", asset.getAssetRole());
        response.put("fileUrl", asset.getFileUrl());
        response.put("objectName", asset.getObjectName());
        response.put("originalFilename", asset.getOriginalFilename());
        response.put("contentType", asset.getContentType());
        response.put("size", asset.getSize());
        response.put("prompt", asset.getPrompt());
        response.put("description", asset.getDescription());
        response.put("status", asset.getStatus());
        response.put("createdAt", asset.getCreatedAt());
        response.put("productId", productId);
        return response;
    }
}
