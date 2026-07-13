package kr.co.daeun.avenstudiobe.domain.detailpage.validator;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerateReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyImageSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyTemplateBlockReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.TextSlotReqDto;
import org.springframework.stereotype.Component;

@Component
public class CopyGenerateRequestValidator {

    public void validate(CopyGenerateReqDto request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }

        List<CopyTemplateBlockReqDto> blocks = safeList(request.getTemplateBlocks());

        validateDuplicateBlockOrders(blocks);
        validateDuplicateInstanceKeys(blocks);
        validateDuplicateImageSlotKeys(blocks);
        validateDuplicateTextSlotKeys(blocks);
        validateImageAssetIds(blocks);
        validateTextSlotRoles(blocks);
        validateRecommendedMaxLengths(blocks);
    }

    private void validateDuplicateBlockOrders(List<CopyTemplateBlockReqDto> blocks) {
        Set<Integer> blockOrders = new HashSet<>();

        for (CopyTemplateBlockReqDto block : blocks) {
            Integer blockOrder = block == null ? null : block.getBlockOrder();
            if (blockOrder == null) {
                continue;
            }

            if (!blockOrders.add(blockOrder)) {
                throw new IllegalArgumentException("blockOrder must be unique. blockOrder=" + blockOrder);
            }
        }
    }

    private void validateDuplicateInstanceKeys(List<CopyTemplateBlockReqDto> blocks) {
        Set<String> instanceKeys = new HashSet<>();

        for (CopyTemplateBlockReqDto block : blocks) {
            String instanceKey = block == null ? null : block.getInstanceKey();
            if (isBlank(instanceKey)) {
                continue;
            }

            if (!instanceKeys.add(instanceKey)) {
                throw new IllegalArgumentException("instanceKey must be unique. instanceKey=" + instanceKey);
            }
        }
    }

    private void validateDuplicateImageSlotKeys(List<CopyTemplateBlockReqDto> blocks) {
        for (CopyTemplateBlockReqDto block : blocks) {
            Set<String> slotKeys = new HashSet<>();

            for (CopyImageSlotReqDto slot : safeList(block == null ? null : block.getImageSlots())) {
                String slotKey = slot == null ? null : slot.getSlotKey();
                if (isBlank(slotKey)) {
                    continue;
                }

                if (!slotKeys.add(slotKey)) {
                    throw new IllegalArgumentException(
                            "image slotKey must be unique in a block. instanceKey="
                                    + block.getInstanceKey()
                                    + ", slotKey="
                                    + slotKey
                    );
                }
            }
        }
    }

    private void validateDuplicateTextSlotKeys(List<CopyTemplateBlockReqDto> blocks) {
        for (CopyTemplateBlockReqDto block : blocks) {
            Set<String> slotKeys = new HashSet<>();

            for (TextSlotReqDto slot : safeList(block == null ? null : block.getTextSlots())) {
                String slotKey = slot == null ? null : slot.getSlotKey();
                if (isBlank(slotKey)) {
                    continue;
                }

                if (!slotKeys.add(slotKey)) {
                    throw new IllegalArgumentException(
                            "text slotKey must be unique in a block. instanceKey="
                                    + block.getInstanceKey()
                                    + ", slotKey="
                                    + slotKey
                    );
                }
            }
        }
    }

    private void validateImageAssetIds(List<CopyTemplateBlockReqDto> blocks) {
        for (CopyTemplateBlockReqDto block : blocks) {
            for (CopyImageSlotReqDto slot : safeList(block == null ? null : block.getImageSlots())) {
                if (slot != null && slot.getAssetId() == null) {
                    throw new IllegalArgumentException(
                            "imageSlots.assetId is required. instanceKey="
                                    + block.getInstanceKey()
                                    + ", slotKey="
                                    + slot.getSlotKey()
                    );
                }
            }
        }
    }

    private void validateTextSlotRoles(List<CopyTemplateBlockReqDto> blocks) {
        for (CopyTemplateBlockReqDto block : blocks) {
            for (TextSlotReqDto slot : safeList(block == null ? null : block.getTextSlots())) {
                if (slot != null && isBlank(slot.getTextRole())) {
                    throw new IllegalArgumentException(
                            "textSlots.textRole is required. instanceKey="
                                    + block.getInstanceKey()
                                    + ", slotKey="
                                    + slot.getSlotKey()
                    );
                }
            }
        }
    }

    private void validateRecommendedMaxLengths(List<CopyTemplateBlockReqDto> blocks) {
        for (CopyTemplateBlockReqDto block : blocks) {
            for (TextSlotReqDto slot : safeList(block == null ? null : block.getTextSlots())) {
                Integer recommendedMaxLength = slot == null ? null : slot.getRecommendedMaxLength();
                if (recommendedMaxLength == null || recommendedMaxLength <= 0) {
                    throw new IllegalArgumentException(
                            "textSlots.recommendedMaxLength must be greater than 0. instanceKey="
                                    + block.getInstanceKey()
                                    + ", slotKey="
                                    + (slot == null ? null : slot.getSlotKey())
                    );
                }
            }
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return Objects.requireNonNullElse(values, List.of());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
