package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewImageSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewTemplateBlockReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewTextSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewBlockContext;
import org.junit.jupiter.api.Test;

class ReviewBlockContextBuilderTest {

    private final ReviewBlockContextBuilder builder = new ReviewBlockContextBuilder();

    @Test
    void sortsBlocksAndSeparatesPlacedFromImmediateNeighboringImages() {
        List<ReviewBlockContext> blocks = builder.build(List.of(
                block(5, "block-5", "MIXED", List.of(image("image-5", 1, 55L)),
                        List.of(text("copy-5", 1, "current"))),
                block(2, "block-2", "IMAGE", List.of(),
                        List.of(text("copy-2b", 2, "second"), text("copy-2a", 1, "first"))),
                block(4, "block-4", "TEXT", List.of(),
                        List.of(text("copy-4", 1, "current"))),
                block(1, "block-1", "IMAGE",
                        List.of(image("image-1b", 2, 11L), image("image-1a", 1, 12L)), List.of()),
                block(3, "block-3", "TEXT", List.of(),
                        List.of(text("copy-3", 1, "  ")))
        ));

        assertEquals(List.of(1, 2, 3, 4, 5), blocks.stream()
                .map(ReviewBlockContext::blockOrder)
                .toList());

        assertEquals(List.of(12L, 11L), blocks.get(0).placedImageAssetIds());
        assertEquals(List.of(), blocks.get(0).neighboringImageAssetIds());

        assertEquals(List.of("copy-2a", "copy-2b"), blocks.get(1).textSlots().stream()
                .map(ReviewTextSlotReqDto::slotKey)
                .toList());
        assertEquals(List.of(), blocks.get(1).placedImageAssetIds());
        assertEquals(List.of(12L, 11L), blocks.get(1).neighboringImageAssetIds());

        assertEquals(List.of(), blocks.get(2).textSlots());
        assertEquals(List.of(), blocks.get(2).neighboringImageAssetIds());

        assertEquals(List.of(), blocks.get(3).neighboringImageAssetIds());

        assertEquals(List.of(55L), blocks.get(4).placedImageAssetIds());
        assertEquals(List.of(), blocks.get(4).neighboringImageAssetIds());
        assertFalse(blocks.get(4).imagesOmitted());
    }

    private ReviewTemplateBlockReqDto block(
            int blockOrder,
            String instanceKey,
            String templateType,
            List<ReviewImageSlotReqDto> imageSlots,
            List<ReviewTextSlotReqDto> textSlots
    ) {
        return new ReviewTemplateBlockReqDto(
                blockOrder,
                instanceKey,
                "template-" + blockOrder,
                templateType,
                imageSlots,
                textSlots
        );
    }

    private ReviewImageSlotReqDto image(String slotKey, int slotOrder, Long assetId) {
        return new ReviewImageSlotReqDto(slotKey, slotOrder, assetId);
    }

    private ReviewTextSlotReqDto text(String slotKey, int slotOrder, String currentContent) {
        return new ReviewTextSlotReqDto(slotKey, slotOrder, "BODY", 100, currentContent);
    }
}
