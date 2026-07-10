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
import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Service
@RequiredArgsConstructor
public class AssetService {

    private final GcsService gcsService;
    private final AssetRepository assetRepository;
    private static final String IMAGE_TYPE = "IMAGE";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String DEFAULT_PRODUCT_IMAGE_ROLE = "PRODUCT_IMAGE";
    private static final String GENERATED_RESULT_ROLE = "GENERATED_RESULT";
    private static final Set<String> CLIENT_UPLOAD_ROLES = Set.of(
            "PRODUCT_FRONT",
            "PRODUCT_BACK",
            "DETAIL",
            "PRODUCT_IMAGE"
    );
    private static final Set<String> SINGLE_SLOT_ROLES = Set.of(
            "PRODUCT_FRONT",
            "PRODUCT_BACK"
    );
    
    // 이미지 한 장 업로드
    @Transactional
    public Asset uploadProductImage(MultipartFile file, Product product) {
        return uploadProductImage(file, product, DEFAULT_PRODUCT_IMAGE_ROLE);
    }

    @Transactional
    public Asset uploadProductImage(MultipartFile file, Product product, String assetRole) {
        String normalizedRole = normalizeAssetRole(assetRole);
        validateSingleSlotRoleAvailable(product.getId(), normalizedRole);

        GcsService.UploadedFile uploaded = gcsService.upload(file, product.getId());

        try {
            Asset asset = Asset.builder()
                    .type(IMAGE_TYPE)
                    .assetRole(normalizedRole)
                    .fileUrl(uploaded.fileUrl())
                    .objectName(uploaded.objectName())
                    .originalFilename(uploaded.originalFilename())
                    .contentType(uploaded.contentType())
                    .size(uploaded.size())
                    .status(ACTIVE_STATUS)
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

        List<String> normalizedRoles = normalizeAndValidateAssetRoles(assetRoles, product.getId());
        List<String> uploadedObjectNames = new ArrayList<>();

        try {
            List<Asset> assets = new ArrayList<>();

            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                String assetRole = normalizedRoles.get(i);
                GcsService.UploadedFile uploaded = gcsService.upload(file, product.getId());
                uploadedObjectNames.add(uploaded.objectName());

                Asset asset = Asset.builder()
                        .type(IMAGE_TYPE)
                        .assetRole(assetRole)
                        .fileUrl(uploaded.fileUrl())
                        .objectName(uploaded.objectName())
                        .originalFilename(uploaded.originalFilename())
                        .contentType(uploaded.contentType())
                        .size(uploaded.size())
                        .status(ACTIVE_STATUS)
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
                .map(this::toAssetImageResult)
                .toList();
    }

    public AssetImageResult toAssetImageResult(Asset asset) {
        return new AssetImageResult(
                asset.getId(),
                asset.getAssetRole(),
                gcsService.generateSignedUrl(asset.getObjectName()),
                asset.getObjectName(),
                asset.getOriginalFilename(),
                asset.getContentType(),
                asset.getSize()
        );
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

    private List<String> normalizeAndValidateAssetRoles(List<String> assetRoles, Long productId) {
        List<String> normalizedRoles = new ArrayList<>();
        Set<String> requestedSingleSlotRoles = new HashSet<>();

        for (String assetRole : assetRoles) {
            String normalizedRole = normalizeAssetRole(assetRole);

            if (isSingleSlotRole(normalizedRole) && !requestedSingleSlotRoles.add(normalizedRole)) {
                throw new IllegalArgumentException(getSingleSlotDuplicateMessage(normalizedRole));
            }

            validateSingleSlotRoleAvailable(productId, normalizedRole);
            normalizedRoles.add(normalizedRole);
        }

        return normalizedRoles;
    }

    private String normalizeAssetRole(String assetRole) {
        if (assetRole == null || assetRole.isBlank()) {
            throw new IllegalArgumentException("이미지 역할을 입력해주세요.");
        }

        String normalizedRole = assetRole.trim().toUpperCase();

        if (!CLIENT_UPLOAD_ROLES.contains(normalizedRole)) {
            throw new IllegalArgumentException("지원하지 않는 이미지 역할입니다. role=" + normalizedRole);
        }

        return normalizedRole;
    }

    private void validateSingleSlotRoleAvailable(Long productId, String assetRole) {
        if (!isSingleSlotRole(assetRole)) {
            return;
        }

        boolean exists = assetRepository.existsByProductIdAndAssetRoleAndTypeAndStatus(
                productId,
                assetRole,
                IMAGE_TYPE,
                ACTIVE_STATUS
        );

        if (exists) {
            throw new IllegalArgumentException(getSingleSlotDuplicateMessage(assetRole));
        }
    }

    private boolean isSingleSlotRole(String assetRole) {
        return SINGLE_SLOT_ROLES.contains(assetRole);
    }

    private String getSingleSlotDuplicateMessage(String assetRole) {
        if ("PRODUCT_FRONT".equals(assetRole)) {
            return "이미 등록된 상품 앞면 이미지가 있습니다.";
        }

        if ("PRODUCT_BACK".equals(assetRole)) {
            return "이미 등록된 상품 뒷면 이미지가 있습니다.";
        }

        return "이미 등록된 상품 이미지가 있습니다.";
    }
}
