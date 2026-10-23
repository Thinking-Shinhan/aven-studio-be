package kr.co.daeun.avenstudiobe.domain.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import kr.co.daeun.avenstudiobe.domain.asset.repository.AssetRepository;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.product.dto.ProductListRespDto;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import kr.co.daeun.avenstudiobe.domain.user.service.UsersService;
import kr.co.daeun.avenstudiobe.global.exception.ProductNotFoundException;
import kr.co.daeun.avenstudiobe.global.storage.GcsService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;

class ProductServiceTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final AssetRepository assetRepository = mock(AssetRepository.class);
    private final GcsService gcsService = mock(GcsService.class);
    private final UsersService usersService = mock(UsersService.class);
    private final ProductService service = new ProductService(
            productRepository,
            assetRepository,
            gcsService,
            usersService
    );

    @Test
    void throwsProductNotFoundWhenProductIsNotAccessibleToAuthenticatedBrand() {
        Authentication authentication = mock(Authentication.class);
        User user = mock(User.class);
        Brand brand = mock(Brand.class);

        when(authentication.getName()).thenReturn("user");
        when(usersService.findByLoginId("user")).thenReturn(user);
        when(user.getBrand()).thenReturn(brand);
        when(brand.getId()).thenReturn(10L);
        when(productRepository.findByIdAndBrand_Id(1L, 10L)).thenReturn(Optional.empty());

        ProductNotFoundException exception = assertThrows(
                ProductNotFoundException.class,
                () -> service.getAccessibleProduct(1L, authentication)
        );

        assertEquals("상품 정보를 찾을 수 없습니다.", exception.getMessage());
        verify(productRepository).findByIdAndBrand_Id(1L, 10L);
    }

    @Test
    void locksAccessibleProductForUpdate() {
        Authentication authentication = mock(Authentication.class);
        User user = mock(User.class);
        Brand brand = mock(Brand.class);
        Product product = mock(Product.class);

        when(authentication.getName()).thenReturn("user");
        when(usersService.findByLoginId("user")).thenReturn(user);
        when(user.getBrand()).thenReturn(brand);
        when(brand.getId()).thenReturn(10L);
        when(productRepository.findAccessibleProductForUpdate(1L, 10L)).thenReturn(Optional.of(product));

        Product result = service.getAccessibleProductForUpdate(1L, authentication);

        assertEquals(product, result);
        verify(productRepository).findAccessibleProductForUpdate(1L, 10L);
    }

    @Test
    void returnsFrontAndBackImagesWithProductList() {
        Product product = mock(Product.class);
        Asset front = mock(Asset.class);
        Asset back = mock(Asset.class);
        PageRequest pageable = PageRequest.of(0, 20);

        when(product.getId()).thenReturn(23L);
        when(productRepository.searchProductsWithoutCategory(2L, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(product), pageable, 1));
        when(assetRepository.findProductImagesForProductList(
                List.of(23L),
                Set.of("PRODUCT_FRONT", "PRODUCT_BACK")
        )).thenReturn(List.of(front, back));

        when(front.getId()).thenReturn(15L);
        when(front.getAssetRole()).thenReturn("PRODUCT_FRONT");
        when(front.getObjectName()).thenReturn("products/23/front.png");
        when(front.getProduct()).thenReturn(product);
        when(back.getId()).thenReturn(16L);
        when(back.getAssetRole()).thenReturn("PRODUCT_BACK");
        when(back.getObjectName()).thenReturn("products/23/back.png");
        when(back.getProduct()).thenReturn(product);
        when(gcsService.generateSignedUrl("products/23/front.png")).thenReturn("front-url");
        when(gcsService.generateSignedUrl("products/23/back.png")).thenReturn("back-url");

        ProductListRespDto response = service.getProductList(
                2L, null, null, null, null, pageable
        );

        assertEquals(15L, response.getProducts().getContent().get(0).getProductImages().front().assetId());
        assertEquals("front-url", response.getProducts().getContent().get(0).getProductImages().front().imageUrl());
        assertEquals(16L, response.getProducts().getContent().get(0).getProductImages().back().assetId());
        assertEquals("back-url", response.getProducts().getContent().get(0).getProductImages().back().imageUrl());
    }
}
