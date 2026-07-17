package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.co.daeun.avenstudiobe.domain.asset.repository.AssetRepository;
import kr.co.daeun.avenstudiobe.domain.asset.repository.ReviewAssetProjection;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.brandmood.entity.BrandMood;
import kr.co.daeun.avenstudiobe.domain.brandmood.repository.BrandMoodRepository;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.BrandReviewContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewContext.ProductReviewContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewContext.ReviewMaterialContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewImageSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewAssetSource;
import kr.co.daeun.avenstudiobe.domain.product.entity.Material;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.mapper.ProductAttributeTextMapper;
import kr.co.daeun.avenstudiobe.domain.product.repository.MaterialRepository;
import kr.co.daeun.avenstudiobe.domain.product.service.ProductService;
import kr.co.daeun.avenstudiobe.global.exception.InvalidAssetPlacementException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CopyReviewContextLoader {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final ProductService productService;
    private final MaterialRepository materialRepository;
    private final BrandMoodRepository brandMoodRepository;
    private final AssetRepository assetRepository;
    private final ProductAttributeTextMapper productAttributeTextMapper;
    private final ReviewBlockContextBuilder reviewBlockContextBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public CopyReviewContext load(CopyReviewReqDto request, Authentication authentication) {
        Product product = productService.getAccessibleProduct(request.productId(), authentication);
        ProductReviewContext productContext = toProductContext(product);
        BrandReviewContext brandContext = toBrandContext(product.getBrand());

        Set<Long> requestedAssetIds = collectAssetIds(request);
        Map<Long, ReviewAssetSource> assetSources = loadAssetSources(request.productId(), requestedAssetIds);
        if (!assetSources.keySet().equals(requestedAssetIds)) {
            throw new InvalidAssetPlacementException();
        }

        return new CopyReviewContext(
                productContext,
                brandContext,
                reviewBlockContextBuilder.build(request.templateBlocks()),
                assetSources.values().stream()
                        .sorted(Comparator.comparing(ReviewAssetSource::assetId))
                        .toList()
        );
    }

    private ProductReviewContext toProductContext(Product product) {
        List<ReviewMaterialContext> materials = materialRepository.findByProductId(product.getId()).stream()
                .map(this::toMaterialContext)
                .toList();

        return new ProductReviewContext(
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getGender(),
                product.getColor(),
                product.getDescription(),
                product.getFitDescription(),
                product.getFabricationDescription(),
                materials,
                productAttributeTextMapper.toLiningText(product.getLining()),
                productAttributeTextMapper.toElasticityText(product.getElasticity()),
                productAttributeTextMapper.toTransparencyText(product.getTransparency()),
                productAttributeTextMapper.toTouchFeelingText(product.getTouchFeeling()),
                productAttributeTextMapper.toThicknessText(product.getThickness())
        );
    }

    private ReviewMaterialContext toMaterialContext(Material material) {
        return new ReviewMaterialContext(material.getName(), material.getPercent());
    }

    private BrandReviewContext toBrandContext(Brand brand) {
        if (brand == null) {
            return null;
        }

        BrandMood brandMood = brandMoodRepository
                .findTopByBrandIdAndActiveTrueOrderByVersionDesc(brand.getId())
                .orElse(null);

        return new BrandReviewContext(
                brand.getName(),
                brandMood == null ? null : brandMood.getBrandSummary(),
                brandMood == null ? null : brandMood.getDesignPhilosophy(),
                brandMood == null ? List.of() : parseToneKeywords(brandMood.getToneKeywords()),
                brandMood == null ? null : brandMood.getBrandInstruction(),
                brandMood == null ? null : brandMood.getCustomBrandNote()
        );
    }

    private List<String> parseToneKeywords(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            List<String> toneKeywords = objectMapper.readValue(json, STRING_LIST_TYPE);
            return toneKeywords == null ? List.of() : List.copyOf(toneKeywords);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("브랜드 무드 toneKeywords 파싱에 실패했습니다.", e);
        }
    }

    private Set<Long> collectAssetIds(CopyReviewReqDto request) {
        return safeList(request.templateBlocks()).stream()
                .filter(Objects::nonNull)
                .flatMap(block -> safeList(block.imageSlots()).stream())
                .filter(Objects::nonNull)
                .map(ReviewImageSlotReqDto::assetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Map<Long, ReviewAssetSource> loadAssetSources(Long productId, Set<Long> assetIds) {
        if (assetIds.isEmpty()) {
            return Map.of();
        }

        return assetRepository.findReviewAssets(productId, assetIds).stream()
                .map(this::toAssetSource)
                .collect(Collectors.toMap(
                        ReviewAssetSource::assetId,
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    private ReviewAssetSource toAssetSource(ReviewAssetProjection asset) {
        return new ReviewAssetSource(
                asset.getId(),
                asset.getObjectName(),
                asset.getContentType(),
                asset.getAssetRole(),
                asset.getDescription()
        );
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
