package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewImageSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewTemplateBlockReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto.ReviewTextSlotReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.ReviewBlockContext;
import org.springframework.stereotype.Component;

@Component
public class ReviewBlockContextBuilder {

    private static final String IMAGE_TEMPLATE_TYPE = "IMAGE";

    public List<ReviewBlockContext> build(List<ReviewTemplateBlockReqDto> templateBlocks) {
        List<ReviewTemplateBlockReqDto> sortedBlocks = safeList(templateBlocks).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        ReviewTemplateBlockReqDto::blockOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();

        return java.util.stream.IntStream.range(0, sortedBlocks.size())
                .mapToObj(index -> toContext(sortedBlocks, index))
                .toList();
    }

    private ReviewBlockContext toContext(List<ReviewTemplateBlockReqDto> sortedBlocks, int index) {
        ReviewTemplateBlockReqDto block = sortedBlocks.get(index);
        List<ReviewTextSlotReqDto> textSlots = collectReviewTargets(block);
        List<Long> placedImageAssetIds = collectImageAssetIds(block);
        ReviewTemplateBlockReqDto previousBlock = index == 0 ? null : sortedBlocks.get(index - 1);
        List<Long> previousImageAssetIds = textSlots.isEmpty()
                || !placedImageAssetIds.isEmpty()
                || previousBlock == null
                ? List.of()
                : collectImageAssetIds(previousBlock);
        boolean previousImageBlock = previousBlock != null
                && IMAGE_TEMPLATE_TYPE.equals(previousBlock.templateType());
        List<Long> associatedImageAssetIds = previousImageBlock
                ? previousImageAssetIds
                : List.of();
        List<Long> neighboringImageAssetIds = previousImageBlock
                ? List.of()
                : previousImageAssetIds;

        return new ReviewBlockContext(
                block.blockOrder(),
                block.instanceKey(),
                block.templateKey(),
                block.templateType(),
                textSlots,
                placedImageAssetIds,
                associatedImageAssetIds,
                neighboringImageAssetIds,
                false
        );
    }

    private List<ReviewTextSlotReqDto> collectReviewTargets(ReviewTemplateBlockReqDto block) {
        return safeList(block.textSlots()).stream()
                .filter(Objects::nonNull)
                .filter(slot -> !isBlank(slot.currentContent()))
                .sorted(Comparator.comparing(
                        ReviewTextSlotReqDto::slotOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .toList();
    }

    private List<Long> collectImageAssetIds(ReviewTemplateBlockReqDto block) {
        return safeList(block.imageSlots()).stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        ReviewImageSlotReqDto::slotOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .map(ReviewImageSlotReqDto::assetId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
