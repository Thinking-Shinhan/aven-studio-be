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
        List<Long> neighboringImageAssetIds = textSlots.isEmpty()
                || !placedImageAssetIds.isEmpty()
                || index == 0
                ? List.of()
                : collectImageAssetIds(sortedBlocks.get(index - 1));

        return new ReviewBlockContext(
                block.blockOrder(),
                block.instanceKey(),
                block.templateKey(),
                block.templateType(),
                textSlots,
                placedImageAssetIds,
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
