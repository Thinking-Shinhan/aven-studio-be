package kr.co.daeun.avenstudiobe.domain.sheet.dto;

import java.math.BigDecimal;

public record SheetMaterialRow(
        String productCode,
        String name,
        BigDecimal percent
) {
}
