package kr.co.daeun.avenstudiobe.domain.library.service;

import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import kr.co.daeun.avenstudiobe.domain.asset.repository.AssetRepository;
import kr.co.daeun.avenstudiobe.domain.asset.repository.LibraryImageFolderProjection;
import kr.co.daeun.avenstudiobe.domain.detailpage.entity.DetailPage;
import kr.co.daeun.avenstudiobe.domain.detailpage.repository.DetailPageRepository;
import kr.co.daeun.avenstudiobe.domain.library.dto.DetailPageRespDto;
import kr.co.daeun.avenstudiobe.domain.library.dto.ImageFolderListRespDto;
import kr.co.daeun.avenstudiobe.domain.library.dto.LibraryAssetListRespDto;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
import kr.co.daeun.avenstudiobe.domain.product.service.ProductService;
import kr.co.daeun.avenstudiobe.global.exception.LibraryAssetNotFoundException;
import kr.co.daeun.avenstudiobe.global.exception.LibraryFolderNotFoundException;
import kr.co.daeun.avenstudiobe.global.storage.GcsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LibraryServiceTest {

    private AssetRepository assetRepository;
    private ProductRepository productRepository;
    private GcsService gcsService;
    private ProductService productService;
    private DetailPageRepository detailPageRepository;
    private LibraryService libraryService;

    @BeforeEach
    void setUp() {
        assetRepository = mock(AssetRepository.class);
        productRepository = mock(ProductRepository.class);
        gcsService = mock(GcsService.class);
        productService = mock(ProductService.class);
        detailPageRepository = mock(DetailPageRepository.class);
        libraryService = new LibraryService(assetRepository, productRepository, gcsService, productService, detailPageRepository);
    }

    @Test
    void returnsGeneratedImageFoldersWithSignedThumbnailUrls() {
        LibraryImageFolderProjection folder = mock(LibraryImageFolderProjection.class);
        when(folder.getProductId()).thenReturn(10L);
        when(folder.getProductCode()).thenReturn("AVN-TS-001");
        when(folder.getProductName()).thenReturn("카라넥 반팔 폴로 셔츠");
        when(folder.getCategory()).thenReturn("상의");
        when(folder.getAssetCount()).thenReturn(8L);
        when(folder.getThumbnailAssetId()).thenReturn(207L);
        when(folder.getThumbnailObjectName()).thenReturn("generated/10/20/image.png");
        when(assetRepository.findLibraryImageFolders(1L, "상의")).thenReturn(List.of(folder));
        when(gcsService.generateSignedUrl("generated/10/20/image.png")).thenReturn("signed-url");

        ImageFolderListRespDto response = libraryService.getImageFolders(1L, "  상의  ");

        assertEquals(1, response.imageFolders().size());
        assertEquals(8L, response.imageFolders().get(0).assetCount());
        assertEquals("signed-url", response.imageFolders().get(0).thumbnailUrl());
    }

    @Test
    void returnsOnlyGeneratedAssetsForAccessibleProduct() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(10L);
        when(product.getCode()).thenReturn("AVN-TS-001");
        when(product.getName()).thenReturn("카라넥 반팔 폴로 셔츠");
        when(product.getCategory()).thenReturn("상의");
        Asset asset = Asset.builder()
                .id(200L)
                .type("GENERATED")
                .assetRole("FULL_BODY")
                .objectName("generated/10/20/image.png")
                .description("모델 착장컷.")
                .status("GENERATED")
                .createdAt(LocalDateTime.of(2026, 7, 3, 12, 40))
                .build();
        when(productRepository.findByIdAndBrand_Id(10L, 1L)).thenReturn(Optional.of(product));
        when(assetRepository.findByProduct_IdAndProduct_Brand_IdAndTypeAndStatusOrderByCreatedAtDescIdDesc(
                10L, 1L, "GENERATED", "GENERATED"
        )).thenReturn(List.of(asset));
        when(gcsService.generateSignedUrl(asset.getObjectName())).thenReturn("signed-url");

        LibraryAssetListRespDto response = libraryService.getFolderAssets(1L, 10L);

        assertEquals(1, response.assets().size());
        assertEquals("FULL_BODY", response.assets().get(0).assetRole());
        assertEquals("signed-url", response.assets().get(0).imageUrl());
    }

    @Test
    void rejectsProductOutsideLoggedInBrand() {
        when(productRepository.findByIdAndBrand_Id(10L, 1L)).thenReturn(Optional.empty());

        assertThrows(
                LibraryFolderNotFoundException.class,
                () -> libraryService.getFolderAssets(1L, 10L)
        );
    }

    @Test
    void softDeletesGeneratedAssetAndRemovesGcsObject() {
        Asset asset = Asset.builder()
                .id(200L)
                .type("GENERATED")
                .status("GENERATED")
                .objectName("generated/10/20/image.png")
                .build();
        when(assetRepository.findByIdAndProduct_Brand_IdAndTypeAndStatus(
                200L, 1L, "GENERATED", "GENERATED"
        )).thenReturn(Optional.of(asset));

        var response = libraryService.deleteAsset(1L, 200L);

        verify(gcsService).delete("generated/10/20/image.png");
        assertEquals("DELETED", asset.getStatus());
        assertEquals(200L, response.assetId());
    }

    @Test
    void rejectsMissingOrForeignAssetWithoutDeletingGcsObject() {
        when(assetRepository.findByIdAndProduct_Brand_IdAndTypeAndStatus(
                200L, 1L, "GENERATED", "GENERATED"
        )).thenReturn(Optional.empty());

        assertThrows(
                LibraryAssetNotFoundException.class,
                () -> libraryService.deleteAsset(1L, 200L)
        );
        verify(gcsService, never()).delete("generated/10/20/image.png");
    }

    @Test
    void returnsDetailPageWithSignedFileUrlWhenDetailPageExists() {
        Authentication authentication = mock(Authentication.class);
        Product product = mock(Product.class);
        DetailPage detailPage = DetailPage.create(
                product,
                "COMPLETED",
                "raw-file-url",
                "detail-pages/10/detail.pdf",
                "detail.pdf",
                1024L
        );
        when(productService.getAccessibleProductForUpdate(10L, authentication)).thenReturn(product);
        when(detailPageRepository.findByProductId(10L)).thenReturn(Optional.of(detailPage));
        when(gcsService.generateSignedUrl("raw-file-url")).thenReturn("signed-url");

        DetailPageRespDto response = libraryService.getDetailPage(10L, authentication);

        assertEquals(10L, response.getProductId());
        assertTrue(response.isHasDetailPage());
        assertEquals("COMPLETED", response.getDetailPage().getStatus());
        assertEquals("signed-url", response.getDetailPage().getFileUrl());
        assertEquals("detail.pdf", response.getDetailPage().getFileName());
    }

    @Test
    void returnsEmptyDetailPageWhenNoneExistsForProduct() {
        Authentication authentication = mock(Authentication.class);
        Product product = mock(Product.class);
        when(productService.getAccessibleProductForUpdate(10L, authentication)).thenReturn(product);
        when(detailPageRepository.findByProductId(10L)).thenReturn(Optional.empty());

        DetailPageRespDto response = libraryService.getDetailPage(10L, authentication);

        assertEquals(10L, response.getProductId());
        assertFalse(response.isHasDetailPage());
    }
}
