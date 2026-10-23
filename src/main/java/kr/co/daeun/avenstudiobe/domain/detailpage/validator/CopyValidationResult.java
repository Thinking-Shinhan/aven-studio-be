package kr.co.daeun.avenstudiobe.domain.detailpage.validator;

import java.util.List;

public record CopyValidationResult(
        List<CopyValidationError> errors
) {
    public CopyValidationResult {
        errors = errors == null ? List.of() : errors;
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean hasOnlyLengthExceeded() {
        return !isValid()
                && errors.stream()
                .allMatch(error -> error.code() == CopyValidationErrorCode.LENGTH_EXCEEDED);
    }
}
