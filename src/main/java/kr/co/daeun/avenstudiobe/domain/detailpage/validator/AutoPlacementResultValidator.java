package kr.co.daeun.avenstudiobe.domain.detailpage.validator;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.AiPlacementDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.ai.AutoPlacementAiResponseDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AssetPlacementCandidate;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoPlacementContext;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.PlacementSlot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AutoPlacementResultValidator {

    public List<ImagePlacementRespDto> validateAndConvert(
            AutoPlacementAiResponseDto aiResponse,
            AutoPlacementContext context
    ) {
        if (aiResponse == null || aiResponse.placements() == null || aiResponse.placements().isEmpty()) {
            return List.of();
        }

        Map<String, PlacementSlot> slotMap = context.targetSlots().stream()
                .collect(Collectors.toMap(
                        slot -> slotUniqueKey(slot.instanceKey(), slot.slotKey()),
                        Function.identity(),
                        (first, ignored) -> first
                ));

        Map<Long, AssetPlacementCandidate> assetMap = context.assets().stream()
                .filter(asset -> asset.assetId() != null)
                .collect(Collectors.toMap(
                        AssetPlacementCandidate::assetId,
                        Function.identity(),
                        (first, ignored) -> first
                ));

        Set<String> usedSlotKeys = new HashSet<>();
        Set<Long> usedAssetIds = context.slots().stream()
                .map(PlacementSlot::assetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        return aiResponse.placements().stream()
                .map(placement -> validatePlacement(placement, slotMap, assetMap, usedSlotKeys, usedAssetIds))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        ValidPlacement::globalSlotOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .map(ValidPlacement::response)
                .toList();
    }

    private ValidPlacement validatePlacement(
            AiPlacementDto placement,
            Map<String, PlacementSlot> slotMap,
            Map<Long, AssetPlacementCandidate> assetMap,
            Set<String> usedSlotKeys,
            Set<Long> usedAssetIds
    ) {
        if (placement == null) {
            logRejected("NULL_PLACEMENT", null);
            return null;
        }

        String slotUniqueKey = slotUniqueKey(placement.instanceKey(), placement.slotKey());
        PlacementSlot slot = slotMap.get(slotUniqueKey);
        if (slot == null) {
            logRejected("UNKNOWN_SLOT", placement);
            return null;
        }

        AssetPlacementCandidate asset = assetMap.get(placement.assetId());
        if (asset == null) {
            logRejected("UNKNOWN_ASSET", placement);
            return null;
        }

        if (!usedSlotKeys.add(slotUniqueKey)) {
            logRejected("DUPLICATED_SLOT", placement);
            return null;
        }

        if (!usedAssetIds.add(placement.assetId())) {
            logRejected("DUPLICATED_ASSET", placement);
            return null;
        }

        ImagePlacementRespDto response = new ImagePlacementRespDto(
                slot.instanceKey(),
                slot.slotKey(),
                asset.assetId(),
                asset.fileUrl(),
                asset.assetRole()
        );

        log.debug("AI placement accepted. instanceKey={}, slotKey={}, assetId={}, reason={}",
                placement.instanceKey(), placement.slotKey(), placement.assetId(), placement.reason());

        return new ValidPlacement(slot.globalSlotOrder(), response);
    }

    private String slotUniqueKey(String instanceKey, String slotKey) {
        return instanceKey + "::" + slotKey;
    }

    private void logRejected(String reason, AiPlacementDto placement) {
        if (placement == null) {
            log.debug("AI placement rejected. reason={}, instanceKey={}, slotKey={}, assetId={}, aiReason={}",
                    reason, null, null, null, null);
            return;
        }

        log.debug("AI placement rejected. reason={}, instanceKey={}, slotKey={}, assetId={}, aiReason={}",
                reason, placement.instanceKey(), placement.slotKey(), placement.assetId(), placement.reason());
    }

    private record ValidPlacement(
            Integer globalSlotOrder,
            ImagePlacementRespDto response
    ) {
    }
}
