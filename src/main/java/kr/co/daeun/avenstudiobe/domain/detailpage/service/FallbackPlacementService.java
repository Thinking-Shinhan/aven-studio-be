package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AssetPlacementCandidate;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoPlacementContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.PlacementSlot;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.PlacementSlotKey;
import org.springframework.stereotype.Service;

@Service
public class FallbackPlacementService {

    private static final Set<String> MODEL_FRAMINGS = Set.of(
            "FULL_BODY",
            "THREE_QUARTER_BODY",
            "HALF_BODY",
            "CLOSE_UP",
            "WIDE_MARGIN"
    );

    public AutoImagePlacementRespDto place(AutoPlacementContext context) {
        return place(context, List.of());
    }

    public AutoImagePlacementRespDto placeRemaining(
            AutoPlacementContext context,
            List<ImagePlacementRespDto> existingPlacements
    ) {
        return place(context, existingPlacements == null ? List.of() : existingPlacements);
    }

    private AutoImagePlacementRespDto place(
            AutoPlacementContext context,
            List<ImagePlacementRespDto> existingPlacements
    ) {
        if (context == null || context.targetSlots() == null || context.targetSlots().isEmpty()
                || context.assets() == null || context.assets().isEmpty()) {
            Long productId = context == null ? null : context.productId();
            return AutoImagePlacementRespDto.empty(productId);
        }

        Set<PlacementSlotKey> excludedSlotKeys = existingPlacements.stream()
                .map(placement -> new PlacementSlotKey(placement.getInstanceKey(), placement.getSlotKey()))
                .collect(Collectors.toSet());
        List<PlacementSlot> slots = context.targetSlots().stream()
                .filter(slot -> !excludedSlotKeys.contains(new PlacementSlotKey(slot.instanceKey(), slot.slotKey())))
                .sorted(Comparator.comparing(
                        PlacementSlot::globalSlotOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();

        List<ImagePlacementRespDto> placements = new ArrayList<>();
        List<PlacementSlot> unfilledSlots = new ArrayList<>();
        Set<Long> usedAssetIds = context.slots().stream()
                .map(PlacementSlot::assetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        usedAssetIds.addAll(existingPlacements.stream()
                .map(ImagePlacementRespDto::getAssetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        for (PlacementSlot slot : slots) {
            AssetPlacementCandidate asset = chooseAsset(
                    context.assets(), usedAssetIds, placementPhase(slot), slot.templateKey());

            if (asset == null) {
                unfilledSlots.add(slot);
                continue;
            }

            usedAssetIds.add(asset.assetId());
            placements.add(toResponse(slot, asset));
        }

        placeByReusingAssets(context, existingPlacements, placements, unfilledSlots);

        return new AutoImagePlacementRespDto(context.productId(), placements);
    }

    private void placeByReusingAssets(
            AutoPlacementContext context,
            List<ImagePlacementRespDto> existingPlacements,
            List<ImagePlacementRespDto> placements,
            List<PlacementSlot> unfilledSlots
    ) {
        if (unfilledSlots.isEmpty()) {
            return;
        }

        Map<Integer, Long> assetIdByGlobalSlotOrder = new HashMap<>();
        Map<Long, Integer> useCounts = new HashMap<>();

        for (PlacementSlot slot : context.slots()) {
            if (slot.assetId() != null) {
                assetIdByGlobalSlotOrder.put(slot.globalSlotOrder(), slot.assetId());
                useCounts.merge(slot.assetId(), 1, Integer::sum);
            }
        }
        registerPlacements(context, existingPlacements, assetIdByGlobalSlotOrder, useCounts);
        registerPlacements(context, placements, assetIdByGlobalSlotOrder, useCounts);

        for (PlacementSlot slot : unfilledSlots) {
            Long previousAssetId = findPreviousAssetId(assetIdByGlobalSlotOrder, slot.globalSlotOrder());
            AssetPlacementCandidate asset = chooseReusableAsset(
                    context.assets(),
                    useCounts,
                    previousAssetId,
                    placementPhase(slot),
                    slot.templateKey()
            );
            if (asset == null) {
                continue;
            }

            placements.add(toResponse(slot, asset));
            assetIdByGlobalSlotOrder.put(slot.globalSlotOrder(), asset.assetId());
            useCounts.merge(asset.assetId(), 1, Integer::sum);
        }
    }

    private void registerPlacements(
            AutoPlacementContext context,
            List<ImagePlacementRespDto> placements,
            Map<Integer, Long> assetIdByGlobalSlotOrder,
            Map<Long, Integer> useCounts
    ) {
        for (ImagePlacementRespDto placement : placements) {
            if (placement == null || placement.getAssetId() == null) {
                continue;
            }

            context.slots().stream()
                    .filter(slot -> slot.instanceKey().equals(placement.getInstanceKey())
                            && slot.slotKey().equals(placement.getSlotKey()))
                    .findFirst()
                    .ifPresent(slot -> assetIdByGlobalSlotOrder.put(slot.globalSlotOrder(), placement.getAssetId()));
            useCounts.merge(placement.getAssetId(), 1, Integer::sum);
        }
    }

    private AssetPlacementCandidate chooseReusableAsset(
            List<AssetPlacementCandidate> assets,
            Map<Long, Integer> useCounts,
            Long previousAssetId,
            PlacementPhase phase,
            String templateKey
    ) {
        boolean sizeInfoSlot = isSizeInfoTemplate(templateKey);
        return assets.stream()
                .filter(asset -> asset.assetId() != null)
                .filter(asset -> isSizeInfoAsset(asset) == sizeInfoSlot)
                .sorted(Comparator
                        .comparingInt((AssetPlacementCandidate asset) ->
                                Objects.equals(asset.assetId(), previousAssetId) ? 1 : 0)
                        .thenComparingInt(asset -> useCounts.getOrDefault(asset.assetId(), 0))
                        .thenComparingInt(asset -> priority(asset.assetRole(), phase))
                        .thenComparingInt(asset -> Boolean.TRUE.equals(asset.liked()) ? 0 : 1)
                        .thenComparing(AssetPlacementCandidate::assetId))
                .findFirst()
                .orElse(null);
    }

    private Long findPreviousAssetId(Map<Integer, Long> assetIdByGlobalSlotOrder, Integer currentOrder) {
        if (currentOrder == null) {
            return null;
        }

        return assetIdByGlobalSlotOrder.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey() < currentOrder)
                .max(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    private AssetPlacementCandidate chooseAsset(
            List<AssetPlacementCandidate> assets,
            Set<Long> usedAssetIds,
            PlacementPhase phase,
            String templateKey
    ) {
        boolean sizeInfoSlot = isSizeInfoTemplate(templateKey);
        return assets.stream()
                .filter(asset -> asset.assetId() != null && !usedAssetIds.contains(asset.assetId()))
                .filter(asset -> isSizeInfoAsset(asset) == sizeInfoSlot)
                .sorted(Comparator
                        .comparingInt((AssetPlacementCandidate asset) -> priority(asset.assetRole(), phase))
                        .thenComparingInt(asset -> Boolean.TRUE.equals(asset.liked()) ? 0 : 1))
                .findFirst()
                .orElse(null);
    }

    private boolean isSizeInfoTemplate(String templateKey) {
        return templateKey != null && templateKey.toLowerCase().contains("size-info");
    }

    private boolean isSizeInfoAsset(AssetPlacementCandidate asset) {
        return "SIZE_INFO".equalsIgnoreCase(asset.assetRole());
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

        if (normalized.contains("MODEL") || isModelAssetRole(normalized)) {
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

    private boolean isModelAssetRole(String assetRole) {
        return MODEL_FRAMINGS.stream()
                .anyMatch(framing -> assetRole.equals(framing) || assetRole.startsWith(framing + "_"));
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
