package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import java.util.List;
import java.util.Set;

public record CopyGenerationContext(
        ProductCopyContext productContext,
        List<BlockCopyContext> sortedBlocks,
        Set<CopySlotKey> targetSlots
) {
    public CopyGenerationContext {
        sortedBlocks = sortedBlocks == null ? List.of() : sortedBlocks;
        targetSlots = targetSlots == null ? Set.of() : targetSlots;
    }
}
