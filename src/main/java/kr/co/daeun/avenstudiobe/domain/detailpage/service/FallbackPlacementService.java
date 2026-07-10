package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AssetPlacementCandidate;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoPlacementContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.PlacementSlot;
import org.springframework.stereotype.Service;

@Service
public class FallbackPlacementService {

    public AutoImagePlacementRespDto place(AutoPlacementContext context) {
        if (context == null || context.slots() == null || context.slots().isEmpty()
                || context.assets() == null || context.assets().isEmpty()) {
            Long productId = context == null ? null : context.productId();
            return AutoImagePlacementRespDto.empty(productId);
        }

        List<PlacementSlot> slots = context.slots().stream()
                .sorted(Comparator.comparing(
                        PlacementSlot::globalSlotOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();

        List<ImagePlacementRespDto> placements = new ArrayList<>();
        Set<Long> usedAssetIds = new HashSet<>();
        int frontLimit = (int) Math.ceil(slots.size() * 0.5);

        for (PlacementSlot slot : slots) {
            boolean frontSlot = slot.globalSlotOrder() != null && slot.globalSlotOrder() <= frontLimit;
            AssetPlacementCandidate asset = chooseAsset(context.assets(), usedAssetIds, frontSlot);

            if (asset == null) {
                continue;
            }

            usedAssetIds.add(asset.assetId());
            placements.add(toResponse(slot, asset));
        }

        return new AutoImagePlacementRespDto(context.productId(), placements);
    }

    private AssetPlacementCandidate chooseAsset(
            List<AssetPlacementCandidate> assets,
            Set<Long> usedAssetIds,
            boolean frontSlot
    ) {
        return assets.stream()
                .filter(asset -> asset.assetId() != null && !usedAssetIds.contains(asset.assetId()))
                .sorted(Comparator.comparingInt(asset -> priority(asset.assetRole(), frontSlot)))
                .findFirst()
                .orElse(null);
    }

    private int priority(String assetRole, boolean frontSlot) {
        AssetRoleGroup group = classify(assetRole);

        if (frontSlot) {
            return switch (group) {
                case MODEL, PRODUCT -> 0;
                case DETAIL, MATERIAL -> 1;
                case OTHER -> 2;
            };
        }

        return switch (group) {
            case DETAIL, MATERIAL -> 0;
            case PRODUCT, MODEL -> 1;
            case OTHER -> 2;
        };
    }

    private AssetRoleGroup classify(String assetRole) {
        if (assetRole == null || assetRole.isBlank()) {
            return AssetRoleGroup.OTHER;
        }

        String normalized = assetRole.trim().toUpperCase();

        if (normalized.contains("MODEL")) {
            return AssetRoleGroup.MODEL;
        }

        if (normalized.contains("DETAIL")) {
            return AssetRoleGroup.DETAIL;
        }

        if (normalized.contains("MATERIAL") || normalized.contains("FABRIC")) {
            return AssetRoleGroup.MATERIAL;
        }

        if (normalized.contains("PRODUCT")) {
            return AssetRoleGroup.PRODUCT;
        }

        return AssetRoleGroup.OTHER;
    }

    private ImagePlacementRespDto toResponse(PlacementSlot slot, AssetPlacementCandidate asset) {
        return new ImagePlacementRespDto(
                slot.blockOrder(),
                slot.instanceKey(),
                slot.templateKey(),
                slot.slotKey(),
                asset.assetId(),
                asset.fileUrl(),
                asset.assetRole()
        );
    }

    private enum AssetRoleGroup {
        MODEL,
        PRODUCT,
        DETAIL,
        MATERIAL,
        OTHER
    }
}
