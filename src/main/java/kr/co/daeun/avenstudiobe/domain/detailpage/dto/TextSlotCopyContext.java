package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

public record TextSlotCopyContext(
        String slotKey,
        Integer slotOrder,
        String textRole,
        Integer recommendedMaxLength,
        String currentContent,
        boolean generationTarget
) {
}
