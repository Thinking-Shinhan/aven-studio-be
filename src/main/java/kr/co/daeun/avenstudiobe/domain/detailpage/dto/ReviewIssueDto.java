package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

public record ReviewIssueDto(
        String category,
        String instanceKey,
        String slotKey,
        String textRole,
        String targetCopy,
        String issue,
        String suggestion
) {
}
