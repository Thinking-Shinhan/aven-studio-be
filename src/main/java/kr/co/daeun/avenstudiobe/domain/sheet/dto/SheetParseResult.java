package kr.co.daeun.avenstudiobe.domain.sheet.dto;

import java.util.List;

public record SheetParseResult<T>(List<T> validRows, List<RowError> errors) {

    public record RowError(int rowNumber, String reason) {
    }
}
