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
import kr.co.daeun.avenstudiobe.domain.product.service.ProductService;
import kr.co.daeun.avenstudiobe.global.storage.GcsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AutoPlacementContextLoader {

    private final ProductService productService;
    private final AssetRepository assetRepository;
    private final PlacementSlotMapper placementSlotMapper;
    private final GcsService gcsService;

    @Transactional(readOnly = true)
    public AutoPlacementContext load(
            AutoImagePlacementReqDto request,
            Authentication authentication,
            String targetInstanceKey
    ) {
        Product product = productService.getAccessibleProduct(request.getProductId(), authentication);

        List<Asset> assets = assetRepository.findByProductIdOrderByIdAsc(product.getId());
        List<PlacementSlot> slots = placementSlotMapper.map(request.getTemplateBlocks());
        List<PlacementSlot> targetSlots = slots.stream()
                .filter(PlacementSlot::isEmpty)
                .filter(slot -> targetInstanceKey == null || targetInstanceKey.equals(slot.instanceKey()))
                .toList();

        return new AutoPlacementContext(
                product.getId(),
                toProductPlacementInfo(product),
                slots,
                targetSlots,
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
                resolveAssetUrl(asset),
                asset.getIsLiked()
        );
    }

    private String resolveAssetUrl(Asset asset) {
        if (asset.getObjectName() == null || asset.getObjectName().isBlank()) {
            return asset.getFileUrl();
        }

        return gcsService.generateSignedUrl(asset.getObjectName());
    }

    private String resolveAssetRole(Asset asset) {
        if (asset.getAssetRole() != null && !asset.getAssetRole().isBlank()) {
            return asset.getAssetRole();
        }

        return null;
    }
}
