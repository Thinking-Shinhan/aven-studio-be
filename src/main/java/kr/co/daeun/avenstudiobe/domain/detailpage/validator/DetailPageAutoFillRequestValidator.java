package kr.co.daeun.avenstudiobe.domain.detailpage.validator;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillTemplateBlockReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ImageSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.TextSlotReqDto;
import kr.co.daeun.avenstudiobe.global.exception.InvalidAutoFillRequestException;
import org.springframework.stereotype.Component;

@Component
public class DetailPageAutoFillRequestValidator {

    public void validate(DetailPageAutoFillReqDto request) {
        if (request == null) {
            throw new InvalidAutoFillRequestException("request is required.");
        }

        List<DetailPageAutoFillTemplateBlockReqDto> blocks = safeList(request.getTemplateBlocks());
        if (blocks.isEmpty()) {
            throw new InvalidAutoFillRequestException("템플릿 블록 목록은 비어 있을 수 없습니다.");
        }

        Set<Integer> blockOrders = new HashSet<>();
        Set<String> instanceKeys = new HashSet<>();

        for (DetailPageAutoFillTemplateBlockReqDto block : blocks) {
            if (block == null) {
                throw new InvalidAutoFillRequestException("templateBlocks must not contain null blocks.");
            }

            if (block.getBlockOrder() == null) {
                throw new InvalidAutoFillRequestException("blockOrder is required.");
            }

            if (isBlank(block.getInstanceKey())) {
                throw new InvalidAutoFillRequestException("instanceKey is required.");
            }

            if (!blockOrders.add(block.getBlockOrder())) {
                throw new InvalidAutoFillRequestException(
                        "blockOrder must be unique. blockOrder=" + block.getBlockOrder()
                );
            }

            if (!instanceKeys.add(block.getInstanceKey())) {
                throw new InvalidAutoFillRequestException(
                        "instanceKey must be unique. instanceKey=" + block.getInstanceKey()
                );
            }

            validateImageSlots(block);
            validateTextSlots(block);
        }
    }

    private void validateImageSlots(DetailPageAutoFillTemplateBlockReqDto block) {
        Set<String> slotKeys = new HashSet<>();
        Set<Integer> slotOrders = new HashSet<>();

        for (ImageSlotReqDto slot : safeList(block.getImageSlots())) {
            if (slot == null) {
                throw new InvalidAutoFillRequestException(
                        "imageSlots must not contain null slots. instanceKey=" + block.getInstanceKey()
                );
            }

            if (isBlank(slot.getSlotKey()) || slot.getSlotOrder() == null) {
                throw new InvalidAutoFillRequestException(
                        "image slotKey and slotOrder are required. instanceKey=" + block.getInstanceKey()
                );
            }

            if (!slotKeys.add(slot.getSlotKey())) {
                throw new InvalidAutoFillRequestException(
                        "image slotKey must be unique in a block. instanceKey="
                                + block.getInstanceKey() + ", slotKey=" + slot.getSlotKey()
                );
            }

            if (!slotOrders.add(slot.getSlotOrder())) {
                throw new InvalidAutoFillRequestException(
                        "image slotOrder must be unique in a block. instanceKey="
                                + block.getInstanceKey() + ", slotOrder=" + slot.getSlotOrder()
                );
            }
        }
    }

    private void validateTextSlots(DetailPageAutoFillTemplateBlockReqDto block) {
        Set<String> slotKeys = new HashSet<>();
        Set<Integer> slotOrders = new HashSet<>();

        for (TextSlotReqDto slot : safeList(block.getTextSlots())) {
            if (slot == null) {
                throw new InvalidAutoFillRequestException(
                        "textSlots must not contain null slots. instanceKey=" + block.getInstanceKey()
                );
            }

            if (isBlank(slot.getSlotKey()) || slot.getSlotOrder() == null) {
                throw new InvalidAutoFillRequestException(
                        "text slotKey and slotOrder are required. instanceKey=" + block.getInstanceKey()
                );
            }

            if (!slotKeys.add(slot.getSlotKey())) {
                throw new InvalidAutoFillRequestException(
                        "text slotKey must be unique in a block. instanceKey="
                                + block.getInstanceKey() + ", slotKey=" + slot.getSlotKey()
                );
            }

            if (!slotOrders.add(slot.getSlotOrder())) {
                throw new InvalidAutoFillRequestException(
                        "text slotOrder must be unique in a block. instanceKey="
                                + block.getInstanceKey() + ", slotOrder=" + slot.getSlotOrder()
                );
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
