package kr.co.daeun.avenstudiobe.domain.detailpage.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiCopyReviewResponseDto(
        List<AiReviewIssueDto> issues
) {
}
