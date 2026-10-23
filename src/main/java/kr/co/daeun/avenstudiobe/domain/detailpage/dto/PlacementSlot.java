package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

public record PlacementSlot(
        Integer globalSlotOrder,
        Integer blockOrder,
        String instanceKey,
        String templateKey,
        String templateType,
        String slotKey,
        Integer slotOrder,
        Long assetId
) {
    public boolean isFilled() {
        return assetId != null;
    }

    public boolean isEmpty() {
        return assetId == null;
    }
}
