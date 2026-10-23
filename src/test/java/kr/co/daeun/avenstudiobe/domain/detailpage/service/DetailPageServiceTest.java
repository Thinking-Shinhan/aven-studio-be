package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import kr.co.daeun.avenstudiobe.domain.asset.service.AssetService;
import kr.co.daeun.avenstudiobe.domain.detailpage.entity.DetailPage;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageSaveRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.repository.DetailPageRepository;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.repository.MaterialRepository;
import kr.co.daeun.avenstudiobe.domain.product.repository.SizeMeasurementRepository;
import kr.co.daeun.avenstudiobe.domain.product.repository.SizeRepository;
import kr.co.daeun.avenstudiobe.domain.product.service.ProductService;
import kr.co.daeun.avenstudiobe.global.exception.ProductNotFoundException;
import kr.co.daeun.avenstudiobe.global.storage.GcsService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

class DetailPageServiceTest {

    private final ProductService productService = mock(ProductService.class);
    private final MaterialRepository materialRepository = mock(MaterialRepository.class);
    private final SizeRepository sizeRepository = mock(SizeRepository.class);
    private final SizeMeasurementRepository sizeMeasurementRepository = mock(SizeMeasurementRepository.class);
    private final AssetService assetService = mock(AssetService.class);
    private final GcsService gcsService = mock(GcsService.class);
    private final DetailPageRepository detailPageRepository = mock(DetailPageRepository.class);
    private final DetailPageService service = new DetailPageService(
            productService,
            materialRepository,
            sizeRepository,
            sizeMeasurementRepository,
            assetService,
            gcsService,
            detailPageRepository
    );

    @Test
    void loadsInitDataThroughTheAuthenticatedUsersAccessibleProduct() {
        Authentication authentication = mock(Authentication.class);
        Product product = mock(Product.class);
        when(productService.getAccessibleProduct(1L, authentication)).thenReturn(product);
        when(materialRepository.findByProductId(1L)).thenReturn(List.of());
        when(sizeRepository.findByProductIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(assetService.getInitAssets(1L)).thenReturn(List.of());

        service.getInitData(1L, authentication);

        verify(productService).getAccessibleProduct(1L, authentication);
    }

    @Test
    void doesNotLoadDetailDataWhenTheProductIsNotAccessible() {
        Authentication authentication = mock(Authentication.class);
        when(productService.getAccessibleProduct(1L, authentication))
                .thenThrow(new ProductNotFoundException());

        assertThrows(ProductNotFoundException.class, () -> service.getInitData(1L, authentication));

        verifyNoInteractions(materialRepository, sizeRepository, sizeMeasurementRepository, assetService);
    }

    @Test
    void deletesPreviousImageOnlyAfterDatabaseCommit() {
        Authentication authentication = mock(Authentication.class);
        Product product = mock(Product.class);
        MultipartFile file = mock(MultipartFile.class);
        DetailPage existing = mock(DetailPage.class);
        GcsService.UploadedFile uploaded = new GcsService.UploadedFile(
                "https://storage/new.png",
                "products/1/new.png",
                "new.png",
                "image/png",
                100L
        );

        when(productService.getAccessibleProductForUpdate(1L, authentication)).thenReturn(product);
        when(gcsService.upload(file, 1L)).thenReturn(uploaded);
        when(detailPageRepository.findByProductId(1L)).thenReturn(Optional.of(existing));
        when(existing.getObjectName()).thenReturn("products/1/old.png");
        when(detailPageRepository.save(existing)).thenReturn(existing);
        when(gcsService.generateSignedUrl("products/1/new.png")).thenReturn("https://storage/signed-new.png");

        TransactionSynchronizationManager.initSynchronization();
        try {
            DetailPageSaveRespDto response = service.saveDetailPageImage(1L, file, authentication);

            assertEquals("new.png", response.fileName());
            assertEquals("https://storage/signed-new.png", response.fileUrl());
            InOrder saveOrder = inOrder(productService, gcsService);
            saveOrder.verify(productService).getAccessibleProductForUpdate(1L, authentication);
            saveOrder.verify(gcsService).upload(file, 1L);

            verify(gcsService, never()).deleteQuietly("products/1/old.png");
            TransactionSynchronizationManager.getSynchronizations().get(0)
                    .afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

            verify(gcsService).deleteQuietly("products/1/old.png");
            verify(gcsService, never()).deleteQuietly("products/1/new.png");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deletesNewImageWhenDatabaseTransactionRollsBack() {
        Authentication authentication = mock(Authentication.class);
        Product product = mock(Product.class);
        MultipartFile file = mock(MultipartFile.class);
        DetailPage existing = mock(DetailPage.class);
        GcsService.UploadedFile uploaded = new GcsService.UploadedFile(
                "https://storage/new.png",
                "products/1/new.png",
                "new.png",
                "image/png",
                100L
        );

        when(productService.getAccessibleProductForUpdate(1L, authentication)).thenReturn(product);
        when(gcsService.upload(file, 1L)).thenReturn(uploaded);
        when(detailPageRepository.findByProductId(1L)).thenReturn(Optional.of(existing));
        when(existing.getObjectName()).thenReturn("products/1/old.png");
        when(detailPageRepository.save(existing)).thenReturn(existing);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.saveDetailPageImage(1L, file, authentication);
            TransactionSynchronizationManager.getSynchronizations().get(0)
                    .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

            verify(gcsService).deleteQuietly("products/1/new.png");
            verify(gcsService, never()).deleteQuietly("products/1/old.png");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
