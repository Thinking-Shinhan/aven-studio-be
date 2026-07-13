package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AssetPlacementCandidate;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoPlacementContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.PlacementSlot;
import org.springframework.stereotype.Service;

@Service
public class FallbackPlacementService {

    public AutoImagePlacementRespDto place(AutoPlacementContext context) {
        return place(context, Set.of(), Set.of());
    }

    public AutoImagePlacementRespDto placeRemaining(
            AutoPlacementContext context,
            List<ImagePlacementRespDto> existingPlacements
    ) {
        Set<String> usedSlotKeys = existingPlacements == null ? Set.of() : existingPlacements.stream()
                .map(placement -> placement.getInstanceKey() + "::" + placement.getSlotKey())
                .collect(Collectors.toSet());
        Set<Long> usedAssetIds = existingPlacements == null ? Set.of() : existingPlacements.stream()
                .map(ImagePlacementRespDto::getAssetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return place(context, usedSlotKeys, usedAssetIds);
    }

    private AutoImagePlacementRespDto place(
            AutoPlacementContext context,
            Set<String> excludedSlotKeys,
            Set<Long> excludedAssetIds
    ) {
        if (context == null || context.targetSlots() == null || context.targetSlots().isEmpty()
                || context.assets() == null || context.assets().isEmpty()) {
            Long productId = context == null ? null : context.productId();
            return AutoImagePlacementRespDto.empty(productId);
        }

        List<PlacementSlot> slots = context.targetSlots().stream()
                .filter(slot -> !excludedSlotKeys.contains(slot.instanceKey() + "::" + slot.slotKey()))
                .sorted(Comparator.comparing(
                        PlacementSlot::globalSlotOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();

        List<ImagePlacementRespDto> placements = new ArrayList<>();
        Set<Long> usedAssetIds = context.slots().stream()
                .map(PlacementSlot::assetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        usedAssetIds.addAll(excludedAssetIds);
        for (PlacementSlot slot : slots) {
            AssetPlacementCandidate asset = chooseAsset(context.assets(), usedAssetIds, placementPhase(slot));

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
            PlacementPhase phase
    ) {
        return assets.stream()
                .filter(asset -> asset.assetId() != null && !usedAssetIds.contains(asset.assetId()))
                .sorted(Comparator.comparingInt(asset -> priority(asset.assetRole(), phase)))
                .findFirst()
                .orElse(null);
    }

    private PlacementPhase placementPhase(PlacementSlot slot) {
        Integer order = slot.globalSlotOrder();
        if (order == null || order <= 1) {
            return PlacementPhase.HERO;
        }

        if (order <= 3) {
            return PlacementPhase.PRODUCT_OVERVIEW;
        }

        return PlacementPhase.DETAIL_OR_MOOD;
    }

    private int priority(String assetRole, PlacementPhase phase) {
        AssetRoleGroup group = classify(assetRole);

        return switch (phase) {
            case HERO -> switch (group) {
                case MODEL -> 0;
                case PRODUCT_FRONT, PRODUCT -> 1;
                case PRODUCT_BACK -> 2;
                case DETAIL, MATERIAL -> 3;
                case OTHER -> 4;
            };
            case PRODUCT_OVERVIEW -> switch (group) {
                case PRODUCT_FRONT -> 0;
                case PRODUCT_BACK -> 1;
                case PRODUCT -> 2;
                case MODEL -> 3;
                case DETAIL, MATERIAL -> 4;
                case OTHER -> 5;
            };
            case DETAIL_OR_MOOD -> switch (group) {
                case DETAIL, MATERIAL -> 0;
                case MODEL -> 1;
                case PRODUCT_FRONT, PRODUCT_BACK, PRODUCT -> 2;
                case OTHER -> 3;
            };
        };
    }

    private AssetRoleGroup classify(String assetRole) {
        if (assetRole == null || assetRole.isBlank()) {
            return AssetRoleGroup.OTHER;
        }

        String normalized = assetRole.trim().toUpperCase();

        if (normalized.contains("MODEL") || switch (normalized) {
            case "FULL_BODY", "THREE_QUARTER_BODY", "HALF_BODY", "CLOSE_UP", "WIDE_MARGIN" -> true;
            default -> false;
        }) {
            return AssetRoleGroup.MODEL;
        }

        if (normalized.contains("PRODUCT_FRONT") || normalized.contains("FRONT")) {
            return AssetRoleGroup.PRODUCT_FRONT;
        }

        if (normalized.contains("PRODUCT_BACK") || normalized.contains("BACK")) {
            return AssetRoleGroup.PRODUCT_BACK;
        }

        if (normalized.contains("DETAIL")) {
            return AssetRoleGroup.DETAIL;
        }

        if (normalized.contains("MATERIAL") || normalized.contains("FABRIC")) {
            return AssetRoleGroup.MATERIAL;
        }

        if (switch (normalized) {
            case "THREE_QUARTER", "SIDE_VIEW", "FLAT_LAY", "HANGING", "GHOST_MANNEQUIN", "FOLDED" -> true;
            default -> false;
        }) {
            return AssetRoleGroup.PRODUCT;
        }

        if (normalized.contains("PRODUCT")) {
            return AssetRoleGroup.PRODUCT;
        }

        return AssetRoleGroup.OTHER;
    }

    private ImagePlacementRespDto toResponse(PlacementSlot slot, AssetPlacementCandidate asset) {
        return new ImagePlacementRespDto(
                slot.instanceKey(),
                slot.slotKey(),
                asset.assetId(),
                asset.fileUrl(),
                asset.assetRole()
        );
    }

    private enum AssetRoleGroup {
        MODEL,
        PRODUCT_FRONT,
        PRODUCT_BACK,
        PRODUCT,
        DETAIL,
        MATERIAL,
        OTHER
    }

    private enum PlacementPhase {
        HERO,
        PRODUCT_OVERVIEW,
        DETAIL_OR_MOOD
    }
}
