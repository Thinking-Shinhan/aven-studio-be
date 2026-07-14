package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.co.daeun.avenstudiobe.domain.asset.repository.AssetCopyProjection;
import kr.co.daeun.avenstudiobe.domain.asset.repository.AssetRepository;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AssetCopyContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerateReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerationContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyImageSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ProductCopyContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.mapper.ProductCopyContextMapper;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.service.ProductService;
import kr.co.daeun.avenstudiobe.global.exception.InvalidAssetPlacementException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CopyGenerationContextLoader {

    private final ProductService productService;
    private final ProductCopyContextMapper productCopyContextMapper;
    private final AssetRepository assetRepository;
    private final BlockCopyContextBuilder blockCopyContextBuilder;

    @Transactional(readOnly = true)
    public CopyGenerationContext load(
            CopyGenerateReqDto request,
            Authentication authentication,
            String targetInstanceKey
    ) {
        Product product = productService.getAccessibleProduct(request.getProductId(), authentication);
        ProductCopyContext productContext = productCopyContextMapper.from(product);

        Set<Long> assetIds = collectAssetIds(request);
        Map<Long, AssetCopyContext> assetMap = loadAssetMap(request.getProductId(), assetIds);

        if (!assetMap.keySet().equals(assetIds)) {
            throw new InvalidAssetPlacementException();
        }

        BlockCopyContextBuilder.BuildResult buildResult =
                blockCopyContextBuilder.build(request.getTemplateBlocks(), assetMap, targetInstanceKey);

        return new CopyGenerationContext(
                productContext,
                buildResult.sortedBlocks(),
                buildResult.targetSlots()
        );
    }

    private Set<Long> collectAssetIds(CopyGenerateReqDto request) {
        if (request.getTemplateBlocks() == null || request.getTemplateBlocks().isEmpty()) {
            return Set.of();
        }

        return request.getTemplateBlocks().stream()
                .filter(Objects::nonNull)
                .flatMap(block -> safeList(block.getImageSlots()).stream())
                .map(CopyImageSlotReqDto::getAssetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Map<Long, AssetCopyContext> loadAssetMap(Long productId, Set<Long> assetIds) {
        if (assetIds.isEmpty()) {
            return Map.of();
        }

        return assetRepository.findCopyContexts(productId, assetIds).stream()
                .map(this::toAssetCopyContext)
                .collect(Collectors.toMap(
                        AssetCopyContext::assetId,
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    private AssetCopyContext toAssetCopyContext(AssetCopyProjection asset) {
        return new AssetCopyContext(
                asset.getId(),
                asset.getRole(),
                asset.getDescription()
        );
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
