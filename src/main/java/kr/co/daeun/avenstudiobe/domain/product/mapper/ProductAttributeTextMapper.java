package kr.co.daeun.avenstudiobe.domain.product.mapper;

import kr.co.daeun.avenstudiobe.domain.sheet.mapper.ProductAttributeCodes;
import org.springframework.stereotype.Component;

@Component
public class ProductAttributeTextMapper {

    public String toLiningText(int code) {
        return ProductAttributeCodes.toDisplayText(ProductAttributeCodes.AttributeType.LINING, code);
    }

    public String toElasticityText(int code) {
        return ProductAttributeCodes.toDisplayText(ProductAttributeCodes.AttributeType.ELASTICITY, code);
    }

    public String toTransparencyText(int code) {
        return ProductAttributeCodes.toDisplayText(ProductAttributeCodes.AttributeType.TRANSPARENCY, code);
    }

    public String toTouchFeelingText(int code) {
        return ProductAttributeCodes.toDisplayText(ProductAttributeCodes.AttributeType.TOUCH_FEELING, code);
    }

    public String toThicknessText(int code) {
        return ProductAttributeCodes.toDisplayText(ProductAttributeCodes.AttributeType.THICKNESS, code);
    }
}
