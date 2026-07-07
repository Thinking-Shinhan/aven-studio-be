package kr.co.daeun.avenstudiobe.domain.sheet.dto;

import java.math.BigDecimal;

public record SheetSizeMeasurementRow(
        String productCode,
        String sizeLabel,
        String part,
        String measurementName,
        BigDecimal value,
        String unit
) {
}
