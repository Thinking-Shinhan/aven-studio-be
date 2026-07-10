package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import java.util.List;
import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import kr.co.daeun.avenstudiobe.domain.asset.repository.AssetRepository;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AssetPlacementCandidate;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoPlacementContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.PlacementSlot;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ProductPlacementInfo;
import kr.co.daeun.avenstudiobe.domain.detailpage.mapper.PlacementSlotMapper;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
import kr.co.daeun.avenstudiobe.global.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AutoPlacementContextLoader {

    private final ProductRepository productRepository;
    private final AssetRepository assetRepository;
    private final PlacementSlotMapper placementSlotMapper;

    @Transactional(readOnly = true)
    public AutoPlacementContext load(AutoImagePlacementReqDto request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(ProductNotFoundException::new);

        List<Asset> assets = assetRepository.findByProductIdOrderByIdAsc(product.getId());
        List<PlacementSlot> slots = placementSlotMapper.map(request.getTemplateBlocks());

        return new AutoPlacementContext(
                product.getId(),
                toProductPlacementInfo(product),
                slots,
                assets.stream()
                        .map(this::toAssetPlacementCandidate)
                        .toList()
        );
    }

    private ProductPlacementInfo toProductPlacementInfo(Product product) {
        return new ProductPlacementInfo(
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getGender(),
                product.getColor(),
                product.getDescription(),
                product.getFitDescription(),
                product.getFabricationDescription()
        );
    }

    private AssetPlacementCandidate toAssetPlacementCandidate(Asset asset) {
        return new AssetPlacementCandidate(
                asset.getId(),
                resolveAssetRole(asset),
                asset.getDescription(),
                asset.getFileUrl()
        );
    }

    private String resolveAssetRole(Asset asset) {
        if (asset.getAssetRole() != null && !asset.getAssetRole().isBlank()) {
            return asset.getAssetRole();
        }

        return null;
    }
}
