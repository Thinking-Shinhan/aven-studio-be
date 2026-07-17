package kr.co.daeun.avenstudiobe.domain.detailpage.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiReviewIssueDto(
        String category,
        String instanceKey,
        String slotKey,
        String issue,
        String suggestion
) {
}
