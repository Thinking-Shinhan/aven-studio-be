package kr.co.daeun.avenstudiobe.domain.detailpage.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.AiPlacementDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.AutoPlacementAiResponseDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AssetPlacementCandidate;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoPlacementContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.PlacementSlot;
import org.junit.jupiter.api.Test;

class AutoPlacementResultValidatorTest {

    private final AutoPlacementResultValidator validator = new AutoPlacementResultValidator();

    @Test
    void treatsSeparatorLikeCharactersAsOrdinaryPartsOfSlotKeys() {
        PlacementSlot firstSlot = slot(1, "a::b", "c");
        PlacementSlot secondSlot = slot(2, "a", "b::c");
        AutoPlacementContext context = new AutoPlacementContext(
                1L,
                null,
                List.of(firstSlot, secondSlot),
                List.of(firstSlot, secondSlot),
                List.of(asset(1L), asset(2L))
        );
        AutoPlacementAiResponseDto response = new AutoPlacementAiResponseDto(List.of(
                new AiPlacementDto("a::b", "c", 1L, "first"),
                new AiPlacementDto("a", "b::c", 2L, "second")
        ));

        var placements = validator.validateAndConvert(response, context);

        assertEquals(2, placements.size());
        assertEquals(List.of(1L, 2L), placements.stream()
                .map(placement -> placement.getAssetId())
                .toList());
    }

    private PlacementSlot slot(int order, String instanceKey, String slotKey) {
        return new PlacementSlot(
                order,
                order,
                instanceKey,
                "template",
                "IMAGE",
                slotKey,
                1,
                null
        );
    }

    private AssetPlacementCandidate asset(Long assetId) {
        return new AssetPlacementCandidate(
                assetId,
                "PRODUCT_FRONT",
                "description-" + assetId,
                "https://example.com/" + assetId,
                false
        );
    }
}
