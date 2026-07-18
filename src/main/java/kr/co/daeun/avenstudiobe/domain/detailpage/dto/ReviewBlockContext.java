package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import java.util.List;

public record ReviewBlockContext(
        Integer blockOrder,
        String instanceKey,
        String templateKey,
        String templateType,
        List<CopyReviewReqDto.ReviewTextSlotReqDto> textSlots,
        List<Long> placedImageAssetIds,
        List<Long> associatedImageAssetIds,
        List<Long> neighboringImageAssetIds,
        boolean imagesOmitted
) {

    public ReviewBlockContext(
            Integer blockOrder,
            String instanceKey,
            String templateKey,
            String templateType,
            List<CopyReviewReqDto.ReviewTextSlotReqDto> textSlots,
            List<Long> placedImageAssetIds,
            List<Long> neighboringImageAssetIds,
            boolean imagesOmitted
    ) {
        this(
                blockOrder,
                instanceKey,
                templateKey,
                templateType,
                textSlots,
                placedImageAssetIds,
                List.of(),
                neighboringImageAssetIds,
                imagesOmitted
        );
    }
}
