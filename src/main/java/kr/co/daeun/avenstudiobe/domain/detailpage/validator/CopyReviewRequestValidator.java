package kr.co.daeun.avenstudiobe.domain.detailpage.validator;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewImageSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewTemplateBlockReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewTextSlotReqDto;
import org.springframework.stereotype.Component;

@Component
public class CopyReviewRequestValidator {

    public void validate(CopyReviewReqDto request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required.");
        }

        List<ReviewTemplateBlockReqDto> blocks = safeList(request.templateBlocks());
        validateDuplicateInstanceKeys(blocks);
        validateDuplicateSlotKeys(blocks);
    }

    private void validateDuplicateInstanceKeys(List<ReviewTemplateBlockReqDto> blocks) {
        Set<String> instanceKeys = new HashSet<>();

        for (ReviewTemplateBlockReqDto block : blocks) {
            String instanceKey = block == null ? null : block.instanceKey();
            if (isBlank(instanceKey)) {
                continue;
            }

            if (!instanceKeys.add(instanceKey)) {
                throw new IllegalArgumentException("instanceKey must be unique. instanceKey=" + instanceKey);
            }
        }
    }

    private void validateDuplicateSlotKeys(List<ReviewTemplateBlockReqDto> blocks) {
        for (ReviewTemplateBlockReqDto block : blocks) {
            if (block == null) {
                continue;
            }

            Set<String> slotKeys = new HashSet<>();
            for (ReviewImageSlotReqDto slot : safeList(block.imageSlots())) {
                addSlotKey(slotKeys, block.instanceKey(), slot == null ? null : slot.slotKey());
            }
            for (ReviewTextSlotReqDto slot : safeList(block.textSlots())) {
                addSlotKey(slotKeys, block.instanceKey(), slot == null ? null : slot.slotKey());
            }
        }
    }

    private void addSlotKey(Set<String> slotKeys, String instanceKey, String slotKey) {
        if (isBlank(slotKey)) {
            return;
        }

        if (!slotKeys.add(slotKey)) {
            throw new IllegalArgumentException(
                    "slotKey must be unique in a block. instanceKey="
                            + instanceKey
                            + ", slotKey="
                            + slotKey
            );
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return Objects.requireNonNullElse(values, List.of());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
