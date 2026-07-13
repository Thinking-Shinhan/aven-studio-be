package kr.co.daeun.avenstudiobe.domain.detailpage.mapper;

import kr.co.daeun.avenstudiobe.domain.brandmood.repository.BrandMoodRepository;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductCopyContextMapperTest {

    private final BrandMoodRepository brandMoodRepository = mock(BrandMoodRepository.class);
    private final ProductCopyContextMapper mapper = new ProductCopyContextMapper(brandMoodRepository);

    @Test
    void mapsProductAttributesToTheirSourceMeaning() {
        Product product = product(1, 2, 3);

        var context = mapper.from(product);

        assertEquals("매우좋음", context.elasticity());
        assertEquals("부드러움", context.touchFeeling());
        assertEquals("얇음", context.thickness());
    }

    @Test
    void doesNotTurnMissingAttributeValuesIntoLow() {
        Product product = product(0, 0, 0);

        var context = mapper.from(product);

        assertNull(context.elasticity());
        assertNull(context.touchFeeling());
        assertNull(context.thickness());
    }

    @Test
    void usesFabricationDescriptionAsTheSingleMaterialContext() {
        Product product = mock(Product.class);
        when(product.getFabricationDescription()).thenReturn("면 60%, 폴리에스터 40%. 가볍고 유연한 원단.");

        var context = mapper.from(product);

        assertEquals("면 60%, 폴리에스터 40%. 가볍고 유연한 원단.", context.fabricationDescription());
    }

    private Product product(int elasticity, int touchFeeling, int thickness) {
        Product product = mock(Product.class);
        when(product.getElasticity()).thenReturn(elasticity);
        when(product.getTouchFeeling()).thenReturn(touchFeeling);
        when(product.getThickness()).thenReturn(thickness);
        return product;
    }
}
