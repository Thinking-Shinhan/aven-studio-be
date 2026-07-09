package kr.co.daeun.avenstudiobe.domain.asset.service;

import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import kr.co.daeun.avenstudiobe.domain.asset.repository.AssetRepository;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AssetInitRespDto;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.global.storage.GcsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class AssetService {

    private final GcsService gcsService;
    private final AssetRepository assetRepository;
    private static final String DEFAULT_PRODUCT_IMAGE_ROLE = "PRODUCT_IMAGE";
    private static final String GENERATED_RESULT_ROLE = "GENERATED_RESULT";
    
    // 이미지 한 장 업로드
    @Transactional
    public Asset uploadProductImage(MultipartFile file, Product product) {
        return uploadProductImage(file, product, DEFAULT_PRODUCT_IMAGE_ROLE);
    }

    @Transactional
    public Asset uploadProductImage(MultipartFile file, Product product, String assetRole) {
        GcsService.UploadedFile uploaded = gcsService.upload(file, product.getId());

        try {
            Asset asset = Asset.builder()
                    .type("IMAGE")
                    .assetRole(assetRole)
                    .fileUrl(uploaded.fileUrl())
                    .objectName(uploaded.objectName())
                    .originalFilename(uploaded.originalFilename())
                    .contentType(uploaded.contentType())
                    .size(uploaded.size())
                    .status("ACTIVE")
                    .product(product)
                    .build();

            return assetRepository.save(asset);

        } catch (RuntimeException e) {
            gcsService.deleteQuietly(uploaded.objectName());
            throw e;
        }
    }
    
    // 이미지 여러 장 업로드
    @Transactional
    public List<Asset> uploadProductImages(List<MultipartFile> files, Product product) {
        List<String> defaultRoles = files.stream()
                .map(file -> DEFAULT_PRODUCT_IMAGE_ROLE)
                .toList();

        return uploadProductImages(files, defaultRoles, product);
    }

    @Transactional
    public List<Asset> uploadProductImages(List<MultipartFile> files, List<String> assetRoles, Product product) {
        if (assetRoles == null || files.size() != assetRoles.size()) {
            throw new IllegalArgumentException("이미지 파일 수와 이미지 역할 수가 일치해야 합니다.");
        }

        List<String> uploadedObjectNames = new ArrayList<>();

        try {
            List<Asset> assets = new ArrayList<>();

            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                GcsService.UploadedFile uploaded = gcsService.upload(file, product.getId());
                uploadedObjectNames.add(uploaded.objectName());

                Asset asset = Asset.builder()
                        .type("IMAGE")
                        .assetRole(assetRoles.get(i))
                        .fileUrl(uploaded.fileUrl())
                        .objectName(uploaded.objectName())
                        .originalFilename(uploaded.originalFilename())
                        .contentType(uploaded.contentType())
                        .size(uploaded.size())
                        .status("ACTIVE")
                        .product(product)
                        .build();

                assets.add(assetRepository.save(asset));
            }

            return assets;

        } catch (RuntimeException e) {
            for (String objectName : uploadedObjectNames) {
                gcsService.deleteQuietly(objectName);
            }

            throw e;
        }
    }

    @Transactional
    public Asset saveGeneratedImage(
            byte[] bytes,
            String contentType,
            Product product,
            Long jobId,
            String prompt
    ) {
        GcsService.UploadedFile uploaded = gcsService.uploadGeneratedImage(bytes, contentType, product.getId(), jobId);

        try {
            Asset asset = Asset.builder()
                    .type("GENERATED")
                    .assetRole(GENERATED_RESULT_ROLE)
                    .fileUrl(uploaded.fileUrl())
                    .objectName(uploaded.objectName())
                    .originalFilename(uploaded.originalFilename())
                    .contentType(uploaded.contentType())
                    .size(uploaded.size())
                    .prompt(prompt)
                    .status("GENERATED")
                    .product(product)
                    .build();

            return assetRepository.save(asset);

        } catch (RuntimeException e) {
            gcsService.deleteQuietly(uploaded.objectName());
            throw e;
        }
    }

    @Transactional
    public void deleteAsset(Asset asset) {
        gcsService.delete(asset.getObjectName());
        assetRepository.delete(asset);
    }

    @Transactional
    public void deleteAssetById(Long assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new RuntimeException("이미지가 존재하지 않습니다."));

        gcsService.delete(asset.getObjectName());
        assetRepository.delete(asset);
    }

    @Transactional
    public void deleteAssetByIdAndBrandId(Long assetId, Long brandId) {
        Asset asset = assetRepository.findByIdAndProduct_Brand_Id(assetId, brandId)
                .orElseThrow(() -> new IllegalArgumentException("이미지가 존재하지 않거나 접근 권한이 없습니다."));

        gcsService.delete(asset.getObjectName());
        assetRepository.delete(asset);
    }

    @Transactional(readOnly = true)
    public List<AssetImageResult> getProductImages(Long productId) {
        List<Asset> assets = assetRepository.findByProductIdOrderByIdDesc(productId);

        return assets.stream()
                .map(asset -> new AssetImageResult(
                        asset.getId(),
                        asset.getAssetRole(),
                        gcsService.generateSignedUrl(asset.getObjectName()),
                        asset.getObjectName(),
                        asset.getOriginalFilename(),
                        asset.getContentType(),
                        asset.getSize()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssetInitRespDto> getInitAssets(Long productId) {
        List<Asset> assets = assetRepository.findByProductIdOrderByIdDesc(productId);

        return assets.stream()
                .map(asset -> new AssetInitRespDto(
                        asset.getId(),
                        gcsService.generateSignedUrl(asset.getObjectName()),
                        asset.getCreatedAt()
                ))
                .toList();
    }


    public record AssetImageResult(
            Long assetId,
            String assetRole,
            String imageUrl,
            String objectName,
            String originalFilename,
            String contentType,
            long size
    ) {}
}
