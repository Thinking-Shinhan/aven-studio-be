package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import java.util.List;

public record CopyReviewRespDto(
        Long productId,
        List<ReviewIssueDto> issues
) {
}
