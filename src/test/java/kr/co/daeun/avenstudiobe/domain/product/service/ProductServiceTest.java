package kr.co.daeun.avenstudiobe.domain.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import kr.co.daeun.avenstudiobe.domain.user.service.UsersService;
import kr.co.daeun.avenstudiobe.global.exception.ProductNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class ProductServiceTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final UsersService usersService = mock(UsersService.class);
    private final ProductService service = new ProductService(productRepository, usersService);

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
}
