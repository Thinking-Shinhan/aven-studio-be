package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import java.util.List;

public record ReviewBlockContext(
        Integer blockOrder,
        String instanceKey,
        String templateKey,
        String templateType,
        List<CopyReviewReqDto.ReviewTextSlotReqDto> textSlots,
        List<Long> placedImageAssetIds,
        List<Long> neighboringImageAssetIds,
        boolean imagesOmitted
) {
}
