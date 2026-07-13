package kr.co.daeun.avenstudiobe.domain.detailpage.mapper;

import kr.co.daeun.avenstudiobe.domain.brandmood.repository.BrandMoodRepository;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ProductCopyContext;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductCopyContextMapper {

    private final BrandMoodRepository brandMoodRepository;

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
                toElasticityText(product.getElasticity()),
                toTouchFeelingText(product.getTouchFeeling()),
                toThicknessText(product.getThickness()),
                resolveBrandTone(product)
        );
    }

    private String toElasticityText(int value) {
        return switch (value) {
            case 1 -> "매우좋음";
            case 2 -> "보통";
            case 3 -> "없음";
            default -> null;
        };
    }

    private String toTouchFeelingText(int value) {
        return switch (value) {
            case 1 -> "쾌적함";
            case 2 -> "부드러움";
            case 3 -> "드라이함";
            default -> null;
        };
    }

    private String toThicknessText(int value) {
        return switch (value) {
            case 1 -> "두꺼움";
            case 2 -> "보통";
            case 3 -> "얇음";
            default -> null;
        };
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
