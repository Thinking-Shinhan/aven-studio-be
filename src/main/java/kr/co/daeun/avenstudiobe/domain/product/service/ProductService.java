package kr.co.daeun.avenstudiobe.domain.product.service;

import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import kr.co.daeun.avenstudiobe.domain.asset.repository.AssetRepository;
import kr.co.daeun.avenstudiobe.domain.product.dto.ProductImageRespDto;
import kr.co.daeun.avenstudiobe.domain.product.dto.ProductImagesRespDto;
import kr.co.daeun.avenstudiobe.domain.product.dto.ProductListRespDto;
import kr.co.daeun.avenstudiobe.domain.product.dto.ProductRespDto;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import kr.co.daeun.avenstudiobe.domain.user.service.UsersService;
import kr.co.daeun.avenstudiobe.global.common.dto.PageRespDto;
import kr.co.daeun.avenstudiobe.global.exception.InvalidCategoryException;
import kr.co.daeun.avenstudiobe.global.exception.InvalidGenderException;
import kr.co.daeun.avenstudiobe.global.exception.ProductNotFoundException;
import kr.co.daeun.avenstudiobe.global.storage.GcsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String PRODUCT_FRONT = "PRODUCT_FRONT";
    private static final String PRODUCT_BACK = "PRODUCT_BACK";
    private static final Set<String> PRODUCT_IMAGE_ROLES = Set.of(PRODUCT_FRONT, PRODUCT_BACK);

    private final ProductRepository productRepository;
    private final AssetRepository assetRepository;
    private final GcsService gcsService;
    private final UsersService usersService;

    public Product getAccessibleProduct(Long productId, Authentication authentication) {
        User user = usersService.findByLoginId(authentication.getName());

        return productRepository.findByIdAndBrand_Id(productId, user.getBrand().getId())
                .orElseThrow(ProductNotFoundException::new);
    }

    public Product getAccessibleProductForUpdate(Long productId, Authentication authentication) {
        User user = usersService.findByLoginId(authentication.getName());

        return productRepository.findAccessibleProductForUpdate(productId, user.getBrand().getId())
                .orElseThrow(ProductNotFoundException::new);
    }

    public ProductListRespDto getProductList(
            Long brandId,
            String category,
            String color,
            String gender,
            String keyword,
            Pageable pageable
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

        Page<Product> productPage;

        if (searchCategories == null) {
            productPage = productRepository.searchProductsWithoutCategory(
                    brandId,
                    normalizedColor,
                    normalizedGender,
                    keywordPattern,
                    pageable
            );
        } else {
            productPage = productRepository.searchProductsWithCategory(
                    brandId,
                    searchCategories,
                    normalizedColor,
                    normalizedGender,
                    keywordPattern,
                    pageable
            );
        }

        Map<Long, ProductImagesRespDto> productImages = findProductImages(productPage.getContent());
        Page<ProductRespDto> productRespPage = productPage.map(product -> ProductRespDto.from(
                product,
                productImages.getOrDefault(product.getId(), ProductImagesRespDto.empty())
        ));

        return new ProductListRespDto(
                category,
                normalizedColor,
                normalizedGender,
                normalizedKeyword,
                PageRespDto.from(productRespPage)
        );
    }

    private Map<Long, ProductImagesRespDto> findProductImages(List<Product> products) {
        if (products.isEmpty()) {
            return Map.of();
        }

        List<Long> productIds = products.stream()
                .map(Product::getId)
                .toList();

        Map<Long, ProductImageRespDto> frontImages = new HashMap<>();
        Map<Long, ProductImageRespDto> backImages = new HashMap<>();

        for (Asset asset : assetRepository.findProductImagesForProductList(productIds, PRODUCT_IMAGE_ROLES)) {
            Long productId = asset.getProduct().getId();
            ProductImageRespDto image = new ProductImageRespDto(
                    asset.getId(),
                    gcsService.generateSignedUrl(asset.getObjectName())
            );

            if (PRODUCT_FRONT.equals(asset.getAssetRole())) {
                frontImages.putIfAbsent(productId, image);
            } else if (PRODUCT_BACK.equals(asset.getAssetRole())) {
                backImages.putIfAbsent(productId, image);
            }
        }

        Map<Long, ProductImagesRespDto> result = new HashMap<>();
        for (Long productId : productIds) {
            result.put(productId, new ProductImagesRespDto(
                    frontImages.get(productId),
                    backImages.get(productId)
            ));
        }
        return result;
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
