package kr.co.daeun.avenstudiobe.domain.detailpage.mapper;

import kr.co.daeun.avenstudiobe.domain.brandmood.repository.BrandMoodRepository;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.mapper.ProductAttributeTextMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductCopyContextMapperTest {

    private final BrandMoodRepository brandMoodRepository = mock(BrandMoodRepository.class);
    private final ProductCopyContextMapper mapper = new ProductCopyContextMapper(
            brandMoodRepository,
            new ProductAttributeTextMapper()
    );

    @Test
    void preservesEveryLegacyGenerateCopyAttributeMapping() {
        assertAttributes(product(1, 1, 1), "매우좋음", "쾌적함", "두꺼움");
        assertAttributes(product(2, 2, 2), "보통", "부드러움", "보통");
        assertAttributes(product(3, 3, 3), "없음", "드라이함", "얇음");
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

    private void assertAttributes(
            Product product,
            String elasticity,
            String touchFeeling,
            String thickness
    ) {
        var context = mapper.from(product);
        assertEquals(elasticity, context.elasticity());
        assertEquals(touchFeeling, context.touchFeeling());
        assertEquals(thickness, context.thickness());
    }
}
