package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import java.util.Objects;

public record CopySlotKey(
        String instanceKey,
        String slotKey
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CopySlotKey that)) {
            return false;
        }
        return Objects.equals(instanceKey, that.instanceKey)
                && Objects.equals(slotKey, that.slotKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceKey, slotKey);
    }
}
