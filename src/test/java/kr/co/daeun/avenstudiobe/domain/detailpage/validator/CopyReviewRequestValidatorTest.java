package kr.co.daeun.avenstudiobe.domain.detailpage.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewImageSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewTemplateBlockReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewTextSlotReqDto;
import org.junit.jupiter.api.Test;

class CopyReviewRequestValidatorTest {

    private final CopyReviewRequestValidator requestValidator = new CopyReviewRequestValidator();
    private final Validator beanValidator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsDuplicateInstanceKeysAcrossTheRequest() {
        CopyReviewReqDto request = request(
                block("block-1", List.of(), List.of()),
                block("block-1", List.of(), List.of())
        );

        assertThrows(IllegalArgumentException.class, () -> requestValidator.validate(request));
    }

    @Test
    void rejectsDuplicateSlotKeysAcrossImageAndTextSlotsInOneBlock() {
        CopyReviewReqDto request = request(block(
                "block-1",
                List.of(imageSlot("shared", null)),
                List.of(textSlot("shared", "copy"))
        ));

        assertThrows(IllegalArgumentException.class, () -> requestValidator.validate(request));
    }

    @Test
    void allowsTheSameSlotKeyInDifferentBlocks() {
        CopyReviewReqDto request = request(
                block("block-1", List.of(imageSlot("shared", 1L)), List.of()),
                block("block-2", List.of(), List.of(textSlot("shared", "copy")))
        );

        assertDoesNotThrow(() -> requestValidator.validate(request));
    }

    @Test
    void allowsEmptyImageSlotsAndBlankCopySlots() {
        CopyReviewReqDto request = request(block(
                "block-1",
                List.of(imageSlot("image-1", null)),
                List.of(textSlot("copy-1", "  "), textSlot("copy-2", null))
        ));

        assertDoesNotThrow(() -> requestValidator.validate(request));
        assertTrue(beanValidator.validate(request).isEmpty());
    }

    @Test
    void reusesExistingTemplateTypeAndTextRoleConstraints() {
        ReviewTemplateBlockReqDto invalidBlock = new ReviewTemplateBlockReqDto(
                1,
                "block-1",
                "template-1",
                "VIDEO",
                List.of(),
                List.of(new ReviewTextSlotReqDto("copy-1", 1, "TITLE", 20, "copy"))
        );

        var violations = beanValidator.validate(new CopyReviewReqDto(1L, List.of(invalidBlock)));

        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString().endsWith("templateType")));
        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString().endsWith("textRole")));
    }

    private CopyReviewReqDto request(ReviewTemplateBlockReqDto... blocks) {
        return new CopyReviewReqDto(1L, List.of(blocks));
    }

    private ReviewTemplateBlockReqDto block(
            String instanceKey,
            List<ReviewImageSlotReqDto> imageSlots,
            List<ReviewTextSlotReqDto> textSlots
    ) {
        return new ReviewTemplateBlockReqDto(
                1,
                instanceKey,
                "template-1",
                "MIXED",
                imageSlots,
                textSlots
        );
    }

    private ReviewImageSlotReqDto imageSlot(String slotKey, Long assetId) {
        return new ReviewImageSlotReqDto(slotKey, 1, assetId);
    }

    private ReviewTextSlotReqDto textSlot(String slotKey, String currentContent) {
        return new ReviewTextSlotReqDto(slotKey, 1, "BODY", 100, currentContent);
    }
}
