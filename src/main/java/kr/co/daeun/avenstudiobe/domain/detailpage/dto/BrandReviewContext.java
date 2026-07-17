package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import java.util.List;

public record BrandReviewContext(
        String brandName,
        String brandSummary,
        String designPhilosophy,
        List<String> toneKeywords,
        String brandInstruction,
        String customBrandNote
) {
}
