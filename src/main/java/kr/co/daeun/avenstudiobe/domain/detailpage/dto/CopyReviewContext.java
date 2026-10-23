package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import java.math.BigDecimal;
import java.util.List;

public record CopyReviewContext(
        ProductReviewContext product,
        BrandReviewContext brand,
        List<ReviewBlockContext> blocks,
        List<ReviewAssetSource> assetSources
) {

    public record ProductReviewContext(
            Long id,
            String name,
            String category,
            String gender,
            String color,
            String description,
            String fitDescription,
            String fabricationDescription,
            List<ReviewMaterialContext> materials,
            String lining,
            String elasticity,
            String transparency,
            String touchFeeling,
            String thickness
    ) {
    }

    public record ReviewMaterialContext(
            String name,
            BigDecimal percent
    ) {
    }
}
