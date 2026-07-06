package kr.co.daeun.avenstudiobe.domain.product.service;

import kr.co.daeun.avenstudiobe.domain.product.dto.ProductListRespDto;
import kr.co.daeun.avenstudiobe.domain.product.dto.ProductRespDto;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
import kr.co.daeun.avenstudiobe.global.exception.InvalidCategoryException;
import kr.co.daeun.avenstudiobe.global.exception.InvalidGenderException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public ProductListRespDto getProductList(
            String category,
            String color,
            String gender,
            String keyword
    ) {
        List<String> searchCategories = resolveSearchCategories(category);

        String normalizedColor = normalize(color);
        String normalizedGender = normalize(gender);
        String normalizedKeyword = normalize(keyword);

        validateGender(normalizedGender);

        String keywordPattern = null;
        if (normalizedKeyword != null) {
            keywordPattern = "%" + normalizedKeyword + "%";
        }

        List<Product> productList;

        if (searchCategories == null) {
            productList = productRepository.searchProductsWithoutCategory(
                    normalizedColor,
                    normalizedGender,
                    keywordPattern
            );
        } else {
            productList = productRepository.searchProductsWithCategory(
                    searchCategories,
                    normalizedColor,
                    normalizedGender,
                    keywordPattern
            );
        }

        List<ProductRespDto> products = productList.stream()
                .map(ProductRespDto::from)
                .toList();

        return new ProductListRespDto(
                category,
                normalizedColor,
                normalizedGender,
                normalizedKeyword,
                products
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private void validateGender(String gender) {
        if (gender == null) {
            return;
        }

        List<String> validGenders = List.of("F", "M", "U");

        if (!validGenders.contains(gender)) {
            throw new InvalidGenderException();
        }
    }

    private List<String> resolveSearchCategories(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }

        return switch (category) {
            case "상의" -> List.of("상의");
            case "하의" -> List.of("바지", "치마");
            case "셋업" -> List.of("셋업");
            case "악세서리" -> List.of("모자", "가방");
            default -> throw new InvalidCategoryException();
        };
    }
}