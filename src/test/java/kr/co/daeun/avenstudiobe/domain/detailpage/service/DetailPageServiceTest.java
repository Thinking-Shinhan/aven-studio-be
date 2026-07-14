package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.co.daeun.avenstudiobe.domain.asset.service.AssetService;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.repository.MaterialRepository;
import kr.co.daeun.avenstudiobe.domain.product.repository.SizeMeasurementRepository;
import kr.co.daeun.avenstudiobe.domain.product.repository.SizeRepository;
import kr.co.daeun.avenstudiobe.domain.product.service.ProductService;
import kr.co.daeun.avenstudiobe.global.exception.ProductNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class DetailPageServiceTest {

    private final ProductService productService = mock(ProductService.class);
    private final MaterialRepository materialRepository = mock(MaterialRepository.class);
    private final SizeRepository sizeRepository = mock(SizeRepository.class);
    private final SizeMeasurementRepository sizeMeasurementRepository = mock(SizeMeasurementRepository.class);
    private final AssetService assetService = mock(AssetService.class);
    private final DetailPageService service = new DetailPageService(
            productService,
            materialRepository,
            sizeRepository,
            sizeMeasurementRepository,
            assetService
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
}
