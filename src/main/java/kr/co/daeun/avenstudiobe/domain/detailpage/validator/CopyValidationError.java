package kr.co.daeun.avenstudiobe.domain.detailpage.validator;

public record CopyValidationError(
        CopyValidationErrorCode code,
        String instanceKey,
        String slotKey,
        String message
) {
}
