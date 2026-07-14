package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.co.daeun.avenstudiobe.domain.asset.repository.AssetRepository;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoPlacementContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.PlacementSlot;
import kr.co.daeun.avenstudiobe.domain.detailpage.mapper.PlacementSlotMapper;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.service.ProductService;
import kr.co.daeun.avenstudiobe.global.storage.GcsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class AutoPlacementContextLoaderTest {

    private ProductService productService;
    private AssetRepository assetRepository;
    private PlacementSlotMapper placementSlotMapper;
    private AutoPlacementContextLoader loader;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        assetRepository = mock(AssetRepository.class);
        placementSlotMapper = mock(PlacementSlotMapper.class);
        loader = new AutoPlacementContextLoader(
                productService,
                assetRepository,
                placementSlotMapper,
                mock(GcsService.class)
        );
        authentication = mock(Authentication.class);

        Product product = mock(Product.class);
        when(product.getId()).thenReturn(1L);
        when(productService.getAccessibleProduct(1L, authentication)).thenReturn(product);
        when(assetRepository.findByProductIdOrderByIdAsc(1L)).thenReturn(List.of());
    }

    @Test
    void templateScopeTargetsOnlyTheRequestedEmptyBlock() {
        PlacementSlot targetEmpty = slot(1, "target", null);
        PlacementSlot nonTargetEmpty = slot(2, "non-target", null);
        when(placementSlotMapper.map(any())).thenReturn(List.of(targetEmpty, nonTargetEmpty));

        AutoPlacementContext context = loader.load(request(), authentication, "target");

        assertEquals(List.of(targetEmpty), context.targetSlots());
        assertEquals(List.of(targetEmpty, nonTargetEmpty), context.slots());
    }

    @Test
    void nullScopeKeepsStandaloneEmptySlotBehavior() {
        PlacementSlot firstEmpty = slot(1, "first", null);
        PlacementSlot secondEmpty = slot(2, "second", null);
        when(placementSlotMapper.map(any())).thenReturn(List.of(firstEmpty, secondEmpty));

        AutoPlacementContext context = loader.load(request(), authentication, null);

        assertEquals(List.of(firstEmpty, secondEmpty), context.targetSlots());
    }

    private AutoImagePlacementReqDto request() {
        AutoImagePlacementReqDto request = new AutoImagePlacementReqDto();
        request.setProductId(1L);
        return request;
    }

    private PlacementSlot slot(int order, String instanceKey, Long assetId) {
        return new PlacementSlot(
                order,
                order,
                instanceKey,
                "template",
                "IMAGE",
                "image",
                1,
                assetId
        );
    }
}
