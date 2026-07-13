package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import java.util.List;

public record BlockCopyContext(
        Integer blockOrder,
        String instanceKey,
        String templateKey,
        List<AssetCopyContext> placedAssets,
        List<AssetCopyContext> referenceAssets,
        List<TextSlotCopyContext> textSlots
) {
    public BlockCopyContext {
        placedAssets = placedAssets == null ? List.of() : placedAssets;
        referenceAssets = referenceAssets == null ? List.of() : referenceAssets;
        textSlots = textSlots == null ? List.of() : textSlots;
    }
}
