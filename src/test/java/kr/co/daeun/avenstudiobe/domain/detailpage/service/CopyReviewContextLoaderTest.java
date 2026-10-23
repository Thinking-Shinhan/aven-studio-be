package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import kr.co.daeun.avenstudiobe.domain.asset.repository.AssetRepository;
import kr.co.daeun.avenstudiobe.domain.asset.repository.ReviewAssetProjection;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.brandmood.entity.BrandMood;
import kr.co.daeun.avenstudiobe.domain.brandmood.repository.BrandMoodRepository;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewImageSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewTemplateBlockReqDto;
import kr.co.daeun.avenstudiobe.domain.product.entity.Material;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.mapper.ProductAttributeTextMapper;
import kr.co.daeun.avenstudiobe.domain.product.repository.MaterialRepository;
import kr.co.daeun.avenstudiobe.domain.product.service.ProductService;
import kr.co.daeun.avenstudiobe.global.exception.InvalidAssetPlacementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class CopyReviewContextLoaderTest {

    private final ProductService productService = mock(ProductService.class);
    private final MaterialRepository materialRepository = mock(MaterialRepository.class);
    private final BrandMoodRepository brandMoodRepository = mock(BrandMoodRepository.class);
    private final AssetRepository assetRepository = mock(AssetRepository.class);
    private final Authentication authentication = mock(Authentication.class);

    private final CopyReviewContextLoader loader = new CopyReviewContextLoader(
            productService,
            materialRepository,
            brandMoodRepository,
            assetRepository,
            new ProductAttributeTextMapper(),
            new ReviewBlockContextBuilder()
    );

    private Product product;

    @BeforeEach
    void setUp() {
        Brand brand = mock(Brand.class);
        when(brand.getId()).thenReturn(7L);
        when(brand.getName()).thenReturn("Aven");

        product = mock(Product.class);
        when(product.getId()).thenReturn(14L);
        when(product.getName()).thenReturn("Jacket");
        when(product.getCategory()).thenReturn("상의");
        when(product.getGender()).thenReturn("U");
        when(product.getColor()).thenReturn("Black");
        when(product.getDescription()).thenReturn("Description");
        when(product.getFitDescription()).thenReturn("Relaxed");
        when(product.getFabricationDescription()).thenReturn("Fabrication");
        when(product.getLining()).thenReturn(2);
        when(product.getElasticity()).thenReturn(1);
        when(product.getTransparency()).thenReturn(3);
        when(product.getTouchFeeling()).thenReturn(2);
        when(product.getThickness()).thenReturn(1);
        when(product.getBrand()).thenReturn(brand);
        when(productService.getAccessibleProduct(14L, authentication)).thenReturn(product);

        Material material = mock(Material.class);
        when(material.getName()).thenReturn("폴리에스터");
        when(material.getPercent()).thenReturn(new BigDecimal("88"));
        when(materialRepository.findByProductId(14L)).thenReturn(List.of(material));

        BrandMood brandMood = mock(BrandMood.class);
        when(brandMood.getBrandSummary()).thenReturn("Summary");
        when(brandMood.getDesignPhilosophy()).thenReturn("Philosophy");
        when(brandMood.getToneKeywords()).thenReturn("[\"미니멀\",\"실용적\"]");
        when(brandMood.getBrandInstruction()).thenReturn("Instruction");
        when(brandMood.getCustomBrandNote()).thenReturn("Note");
        when(brandMoodRepository.findTopByBrandIdAndActiveTrueOrderByVersionDesc(7L))
                .thenReturn(Optional.of(brandMood));
    }

    @Test
    void returnsOnlySnapshotValuesForProductBrandMaterialsAndAssets() {
        ReviewAssetProjection asset = asset(25L);
        when(assetRepository.findReviewAssets(14L, Set.of(25L))).thenReturn(List.of(asset));

        CopyReviewContext context = loader.load(request(25L), authentication);

        assertEquals(14L, context.product().id());
        assertEquals("있음", context.product().lining());
        assertEquals("매우좋음", context.product().elasticity());
        assertEquals("없음", context.product().transparency());
        assertEquals("부드러움", context.product().touchFeeling());
        assertEquals("두꺼움", context.product().thickness());
        assertEquals("폴리에스터", context.product().materials().get(0).name());
        assertEquals(new BigDecimal("88"), context.product().materials().get(0).percent());

        assertEquals("Aven", context.brand().brandName());
        assertEquals(List.of("미니멀", "실용적"), context.brand().toneKeywords());
        assertEquals(25L, context.assetSources().get(0).assetId());
        assertEquals("products/14/25.webp", context.assetSources().get(0).objectName());
        assertEquals("image/webp", context.assetSources().get(0).contentType());
        assertEquals(List.of(25L), context.blocks().get(0).placedImageAssetIds());

        verify(productService).getAccessibleProduct(14L, authentication);
        verify(assetRepository).findReviewAssets(14L, Set.of(25L));
    }

    @Test
    void rejectsAnyRequestedAssetThatDoesNotBelongToTheProduct() {
        ReviewAssetProjection ownedAsset = asset(25L);
        when(assetRepository.findReviewAssets(14L, Set.of(25L, 99L)))
                .thenReturn(List.of(ownedAsset));

        assertThrows(
                InvalidAssetPlacementException.class,
                () -> loader.load(request(25L, 99L), authentication)
        );
    }

    private CopyReviewReqDto request(Long... assetIds) {
        List<ReviewImageSlotReqDto> imageSlots = java.util.stream.IntStream.range(0, assetIds.length)
                .mapToObj(index -> new ReviewImageSlotReqDto(
                        "image-" + index,
                        index + 1,
                        assetIds[index]
                ))
                .toList();
        ReviewTemplateBlockReqDto block = new ReviewTemplateBlockReqDto(
                1,
                "block-1",
                "template-1",
                "MIXED",
                imageSlots,
                List.of()
        );
        return new CopyReviewReqDto(14L, List.of(block));
    }

    private ReviewAssetProjection asset(Long assetId) {
        ReviewAssetProjection asset = mock(ReviewAssetProjection.class);
        when(asset.getId()).thenReturn(assetId);
        when(asset.getObjectName()).thenReturn("products/14/" + assetId + ".webp");
        when(asset.getContentType()).thenReturn("image/webp");
        when(asset.getAssetRole()).thenReturn("MODEL_CUT");
        when(asset.getDescription()).thenReturn("Model image");
        return asset;
    }
}
