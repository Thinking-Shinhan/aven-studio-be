package kr.co.daeun.avenstudiobe.domain.product.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import kr.co.daeun.avenstudiobe.domain.sheet.mapper.ProductAttributeCodes;
import org.junit.jupiter.api.Test;

class ProductAttributeTextMapperTest {

    private final ProductAttributeTextMapper mapper = new ProductAttributeTextMapper();

    @Test
    void mapsAllAttributesFromProductAttributeCodes() {
        assertUsesSourceMap(ProductAttributeCodes.LINING, mapper::toLiningText);
        assertUsesSourceMap(ProductAttributeCodes.ELASTICITY, mapper::toElasticityText);
        assertUsesSourceMap(ProductAttributeCodes.TRANSPARENCY, mapper::toTransparencyText);
        assertUsesSourceMap(ProductAttributeCodes.TOUCH_FEELING, mapper::toTouchFeelingText);
        assertUsesSourceMap(ProductAttributeCodes.THICKNESS, mapper::toThicknessText);
    }

    @Test
    void returnsNullForCodesOutsideTheSourceMaps() {
        assertNull(mapper.toLiningText(0));
        assertNull(mapper.toElasticityText(4));
        assertNull(mapper.toTransparencyText(-1));
        assertNull(mapper.toTouchFeelingText(100));
        assertNull(mapper.toThicknessText(Integer.MAX_VALUE));
    }

    private void assertUsesSourceMap(Map<String, Integer> source, CodeMapper codeMapper) {
        source.forEach((text, code) -> assertEquals(text, codeMapper.map(code)));
    }

    @FunctionalInterface
    private interface CodeMapper {
        String map(int code);
    }
}
