package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AssetPlacementCandidate;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoPlacementContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.PlacementSlot;
import org.junit.jupiter.api.Test;

class FallbackPlacementServiceTest {

    private final FallbackPlacementService service = new FallbackPlacementService();

    @Test
    void reusesAssetsOnlyAfterUniqueAssetsAreExhausted() {
        List<PlacementSlot> slots = List.of(
                slot(1, "image-1", null),
                slot(2, "image-2", null),
                slot(3, "image-3", null),
                slot(4, "image-4", null)
        );
        AutoPlacementContext context = context(
                slots,
                slots,
                List.of(asset(1L, "FULL_BODY"), asset(2L, "FRONT_VIEW"))
        );

        List<Long> placedAssetIds = service.place(context).getPlacements().stream()
                .map(placement -> placement.getAssetId())
                .toList();

        assertEquals(List.of(1L, 2L, 1L, 2L), placedAssetIds);
    }

    @Test
    void doesNotReuseAssetsWhenUniqueAssetsAreAvailable() {
        List<PlacementSlot> slots = List.of(
                slot(1, "image-1", null),
                slot(2, "image-2", null)
        );
        AutoPlacementContext context = context(
                slots,
                slots,
                List.of(asset(1L, "FULL_BODY"), asset(2L, "FRONT_VIEW"))
        );

        List<Long> placedAssetIds = service.place(context).getPlacements().stream()
                .map(placement -> placement.getAssetId())
                .toList();

        assertEquals(List.of(1L, 2L), placedAssetIds);
    }

    @Test
    void reusesAnExistingPageAssetWhenNoUnusedAssetRemains() {
        PlacementSlot filledSlot = slot(1, "image-1", 1L);
        PlacementSlot emptySlot = slot(2, "image-2", null);
        AutoPlacementContext context = context(
                List.of(filledSlot, emptySlot),
                List.of(emptySlot),
                List.of(asset(1L, "FULL_BODY"))
        );

        List<Long> placedAssetIds = service.place(context).getPlacements().stream()
                .map(placement -> placement.getAssetId())
                .toList();

        assertEquals(List.of(1L), placedAssetIds);
    }

    @Test
    void excludesOnlyTheExactExistingSlotWhenKeysContainSeparators() {
        PlacementSlot firstSlot = slot(1, "a::b", "c", null);
        PlacementSlot secondSlot = slot(2, "a", "b::c", null);
        AutoPlacementContext context = context(
                List.of(firstSlot, secondSlot),
                List.of(firstSlot, secondSlot),
                List.of(asset(1L, "FULL_BODY"), asset(2L, "FRONT_VIEW"))
        );
        ImagePlacementRespDto existingPlacement = new ImagePlacementRespDto(
                "a::b",
                "c",
                1L,
                "https://example.com/1",
                "FULL_BODY"
        );

        var placements = service.placeRemaining(context, List.of(existingPlacement)).getPlacements();

        assertEquals(1, placements.size());
        assertEquals("a", placements.get(0).getInstanceKey());
        assertEquals("b::c", placements.get(0).getSlotKey());
        assertEquals(2L, placements.get(0).getAssetId());
    }

    private AutoPlacementContext context(
            List<PlacementSlot> slots,
            List<PlacementSlot> targetSlots,
            List<AssetPlacementCandidate> assets
    ) {
        return new AutoPlacementContext(1L, null, slots, targetSlots, assets);
    }

    private PlacementSlot slot(int globalOrder, String slotKey, Long assetId) {
        return slot(globalOrder, "block-" + globalOrder, slotKey, assetId);
    }

    private PlacementSlot slot(
            int globalOrder,
            String instanceKey,
            String slotKey,
            Long assetId
    ) {
        return new PlacementSlot(
                globalOrder,
                globalOrder,
                instanceKey,
                "template",
                "IMAGE",
                slotKey,
                1,
                assetId
        );
    }

    private AssetPlacementCandidate asset(Long assetId, String assetRole) {
        return new AssetPlacementCandidate(
                assetId,
                assetRole,
                "description-" + assetId,
                "https://example.com/" + assetId,
                false
        );
    }
}
