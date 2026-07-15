package kr.co.daeun.avenstudiobe.domain.library.service;

import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import kr.co.daeun.avenstudiobe.domain.asset.repository.AssetRepository;
import kr.co.daeun.avenstudiobe.domain.asset.repository.LibraryImageFolderProjection;
import kr.co.daeun.avenstudiobe.domain.asset.repository.LibraryPreviewImageProjection;
import kr.co.daeun.avenstudiobe.domain.detailpage.entity.DetailPage;
import kr.co.daeun.avenstudiobe.domain.detailpage.repository.DetailPageRepository;
import kr.co.daeun.avenstudiobe.domain.library.dto.DetailPageDeleteRespDto;
import kr.co.daeun.avenstudiobe.domain.library.dto.DetailPageRespDto;
import kr.co.daeun.avenstudiobe.domain.library.dto.ImageFolderListRespDto;
import kr.co.daeun.avenstudiobe.domain.library.dto.ImageFolderRespDto;
import kr.co.daeun.avenstudiobe.domain.library.dto.LibraryAssetCountRespDto;
import kr.co.daeun.avenstudiobe.domain.library.dto.LibraryAssetDeleteRespDto;
import kr.co.daeun.avenstudiobe.domain.library.dto.LibraryAssetListRespDto;
import kr.co.daeun.avenstudiobe.domain.library.dto.LibraryAssetRespDto;
import kr.co.daeun.avenstudiobe.domain.library.dto.LibraryPreviewImageRespDto;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
import kr.co.daeun.avenstudiobe.domain.product.service.ProductService;
import kr.co.daeun.avenstudiobe.global.exception.DetailPageNotFoundException;
import kr.co.daeun.avenstudiobe.global.exception.LibraryAssetNotFoundException;
import kr.co.daeun.avenstudiobe.global.exception.LibraryFolderNotFoundException;
import kr.co.daeun.avenstudiobe.global.storage.GcsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LibraryService {

    private static final String GENERATED_TYPE = "GENERATED";
    private static final String GENERATED_STATUS = "GENERATED";

    private final AssetRepository assetRepository;
    private final ProductRepository productRepository;
    private final GcsService gcsService;
    private final ProductService productService;
    private final DetailPageRepository detailPageRepository;

    @Transactional(readOnly = true)
    public ImageFolderListRespDto getImageFolders(Long brandId, String category) {
        String normalizedCategory = normalizeCategory(category);

        List<LibraryImageFolderProjection> folderProjections =
                assetRepository.findLibraryImageFolders(brandId, normalizedCategory);

        if (folderProjections.isEmpty()) {
            return new ImageFolderListRespDto(List.of());
        }

        List<Long> productIds = folderProjections.stream()
                .map(LibraryImageFolderProjection::getProductId)
                .toList();

        Map<Long, List<LibraryPreviewImageRespDto>> previewImagesByProductId =
                assetRepository.findLibraryPreviewImages(productIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                LibraryPreviewImageProjection::getProductId,
                                Collectors.mapping(
                                        this::toPreviewImageResponse,
                                        Collectors.toList()
                                )
                        ));

        List<ImageFolderRespDto> folders = folderProjections.stream()
                .map(folder -> toImageFolderResponse(
                        folder,
                        previewImagesByProductId.getOrDefault(folder.getProductId(), List.of())
                ))
                .toList();

        return new ImageFolderListRespDto(folders);
    }

    @Transactional(readOnly = true)
    public LibraryAssetListRespDto getFolderAssets(Long brandId, Long productId) {
        Product product = productRepository.findByIdAndBrand_Id(productId, brandId)
                .orElseThrow(LibraryFolderNotFoundException::new);
        List<LibraryAssetRespDto> assets = assetRepository
                .findByProduct_IdAndProduct_Brand_IdAndTypeAndStatusOrderByCreatedAtDescIdDesc(
                        productId,
                        brandId,
                        GENERATED_TYPE,
                        GENERATED_STATUS
                )
                .stream()
                .map(this::toLibraryAssetResponse)
                .toList();

        return new LibraryAssetListRespDto(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getCategory(),
                assets
        );
    }

    @Transactional
    public LibraryAssetDeleteRespDto deleteAsset(Long brandId, Long assetId) {
        Asset asset = assetRepository.findByIdAndProduct_Brand_IdAndTypeAndStatus(
                        assetId,
                        brandId,
                        GENERATED_TYPE,
                        GENERATED_STATUS
                )
                .orElseThrow(LibraryAssetNotFoundException::new);

        gcsService.delete(asset.getObjectName());
        asset.markDeleted();

        return new LibraryAssetDeleteRespDto(asset.getId(), "이미지가 삭제되었습니다.");
    }

    private ImageFolderRespDto toImageFolderResponse(
            LibraryImageFolderProjection folder,
            List<LibraryPreviewImageRespDto> previewImages
    ) {
        long modelCutCount = valueOrZero(folder.getModelCutCount());
        long productCutCount = valueOrZero(folder.getProductCutCount());
        long detailPageCount = valueOrZero(folder.getDetailPageCount());

        LibraryAssetCountRespDto counts = new LibraryAssetCountRespDto(
                modelCutCount,
                productCutCount,
                detailPageCount,
                modelCutCount + productCutCount + detailPageCount
        );

        return new ImageFolderRespDto(
                folder.getProductId(),
                folder.getProductCode(),
                folder.getProductName(),
                folder.getCategory(),
                previewImages,
                counts,
                folder.getUpdatedAt()
        );
    }

    private LibraryPreviewImageRespDto toPreviewImageResponse(
            LibraryPreviewImageProjection preview
    ) {
        return new LibraryPreviewImageRespDto(
                preview.getAssetId(),
                gcsService.generateSignedUrl(preview.getObjectName())
        );
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private LibraryAssetRespDto toLibraryAssetResponse(Asset asset) {
        return new LibraryAssetRespDto(
                asset.getId(),
                asset.getType(),
                asset.getAssetRole(),
                gcsService.generateSignedUrl(asset.getObjectName()),
                asset.getDescription(),
                asset.getStatus(),
                asset.getCreatedAt()
        );
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        return category.trim();
    }

    @Transactional(readOnly = true)
    public DetailPageRespDto getDetailPage(Long productId, Authentication authentication) {
        productService.getAccessibleProduct(productId, authentication);

        return detailPageRepository.findByProductId(productId)
                .map(detailPage -> DetailPageRespDto.from(
                        productId,
                        detailPage,
                        gcsService.generateSignedUrl(detailPage.getObjectName())
                ))
                .orElseGet(() -> DetailPageRespDto.empty(productId));
    }

    @Transactional
    public DetailPageDeleteRespDto deleteDetailPage(
            Long productId,
            Authentication authentication
    ) {
        productService.getAccessibleProduct(productId, authentication);

        DetailPage detailPage = detailPageRepository.findByProductId(productId)
                .orElseThrow(DetailPageNotFoundException::new);

        gcsService.delete(detailPage.getObjectName());
        detailPageRepository.delete(detailPage);

        return new DetailPageDeleteRespDto(productId, "상세페이지가 삭제되었습니다.");
    }
}
