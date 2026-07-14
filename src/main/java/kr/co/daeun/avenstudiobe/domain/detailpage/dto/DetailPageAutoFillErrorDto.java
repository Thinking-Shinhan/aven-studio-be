package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

public record DetailPageAutoFillErrorDto(
        String stage,
        String code,
        boolean retryable
) {
}
