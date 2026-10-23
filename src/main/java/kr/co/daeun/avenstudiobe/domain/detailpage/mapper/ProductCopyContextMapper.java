package kr.co.daeun.avenstudiobe.domain.detailpage.mapper;

import kr.co.daeun.avenstudiobe.domain.brandmood.repository.BrandMoodRepository;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ProductCopyContext;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.mapper.ProductAttributeTextMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductCopyContextMapper {

    private final BrandMoodRepository brandMoodRepository;
    private final ProductAttributeTextMapper productAttributeTextMapper;

    public ProductCopyContext from(Product product) {
        if (product == null) {
            return null;
        }

        return new ProductCopyContext(
                product.getName(),
                product.getCategory(),
                product.getGender(),
                product.getColor(),
                product.getFitDescription(),
                product.getFabricationDescription(),
                productAttributeTextMapper.toElasticityText(product.getElasticity()),
                productAttributeTextMapper.toTouchFeelingText(product.getTouchFeeling()),
                productAttributeTextMapper.toThicknessText(product.getThickness()),
                resolveBrandTone(product)
        );
    }

    private String resolveBrandTone(Product product) {
        if (product.getBrand() == null) {
            return null;
        }

        return brandMoodRepository.findTopByBrandIdAndActiveTrueOrderByVersionDesc(product.getBrand().getId())
                .map(brandMood -> defaultIfBlank(brandMood.getBrandInstruction(), product.getBrand().getName()))
                .orElse(product.getBrand().getName());
    }

    private String defaultIfBlank(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }
}
