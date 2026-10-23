package kr.co.daeun.avenstudiobe.domain.detailpage.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillTemplateBlockReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ImageSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.TextSlotReqDto;
import kr.co.daeun.avenstudiobe.global.exception.InvalidAutoFillRequestException;
import org.junit.jupiter.api.Test;

class DetailPageAutoFillRequestValidatorTest {

    private final DetailPageAutoFillRequestValidator validator = new DetailPageAutoFillRequestValidator();

    @Test
    void acceptsUniqueBlockAndSlotKeys() {
        DetailPageAutoFillReqDto request = request(
                block("hero", 1, List.of(imageSlot("image-1", 1)), List.of(textSlot("headline", 1))),
                block("detail", 2, List.of(imageSlot("image-2", 1)), List.of(textSlot("body", 1)))
        );

        assertDoesNotThrow(() -> validator.validate(request));
    }

    @Test
    void rejectsEmptyTemplateBlocksWithKoreanMessage() {
        DetailPageAutoFillReqDto request = request();

        InvalidAutoFillRequestException exception = assertThrows(
                InvalidAutoFillRequestException.class,
                () -> validator.validate(request)
        );

        assertEquals("템플릿 블록 목록은 비어 있을 수 없습니다.", exception.getMessage());
    }

    @Test
    void rejectsDuplicateInstanceKeys() {
        DetailPageAutoFillReqDto request = request(
                block("same", 1, List.of(), List.of()),
                block("same", 2, List.of(), List.of())
        );

        assertThrows(InvalidAutoFillRequestException.class, () -> validator.validate(request));
    }

    @Test
    void rejectsDuplicateBlockOrders() {
        DetailPageAutoFillReqDto request = request(
                block("hero", 1, List.of(), List.of()),
                block("detail", 1, List.of(), List.of())
        );

        assertThrows(InvalidAutoFillRequestException.class, () -> validator.validate(request));
    }

    @Test
    void rejectsDuplicateSlotKeysAndOrdersWithinBlock() {
        DetailPageAutoFillReqDto duplicateImageKeys = request(
                block("hero", 1, List.of(imageSlot("image", 1), imageSlot("image", 2)), List.of())
        );
        DetailPageAutoFillReqDto duplicateTextOrders = request(
                block("hero", 1, List.of(), List.of(textSlot("headline", 1), textSlot("body", 1)))
        );

        assertThrows(InvalidAutoFillRequestException.class, () -> validator.validate(duplicateImageKeys));
        assertThrows(InvalidAutoFillRequestException.class, () -> validator.validate(duplicateTextOrders));
    }

    private DetailPageAutoFillReqDto request(DetailPageAutoFillTemplateBlockReqDto... blocks) {
        DetailPageAutoFillReqDto request = new DetailPageAutoFillReqDto();
        request.setProductId(1L);
        request.setTemplateBlocks(List.of(blocks));
        return request;
    }

    private DetailPageAutoFillTemplateBlockReqDto block(
            String instanceKey,
            int blockOrder,
            List<ImageSlotReqDto> imageSlots,
            List<TextSlotReqDto> textSlots
    ) {
        DetailPageAutoFillTemplateBlockReqDto block = new DetailPageAutoFillTemplateBlockReqDto();
        block.setInstanceKey(instanceKey);
        block.setBlockOrder(blockOrder);
        block.setTemplateKey("template-" + instanceKey);
        block.setTemplateType(imageSlots.isEmpty() ? "TEXT" : textSlots.isEmpty() ? "IMAGE" : "MIXED");
        block.setImageSlots(imageSlots);
        block.setTextSlots(textSlots);
        return block;
    }

    private ImageSlotReqDto imageSlot(String slotKey, int slotOrder) {
        ImageSlotReqDto slot = new ImageSlotReqDto();
        slot.setSlotKey(slotKey);
        slot.setSlotOrder(slotOrder);
        return slot;
    }

    private TextSlotReqDto textSlot(String slotKey, int slotOrder) {
        TextSlotReqDto slot = new TextSlotReqDto();
        slot.setSlotKey(slotKey);
        slot.setSlotOrder(slotOrder);
        slot.setTextRole("HEADLINE");
        slot.setRecommendedMaxLength(30);
        return slot;
    }
}
