package kr.co.daeun.avenstudiobe.domain.sheet.mapper;

import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetMaterialRow;
import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetParseResult;
import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetParseResult.RowError;
import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetProductRow;
import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetSizeMeasurementRow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SheetRowParser {

    private static final String FREE_SIZE_LABEL = "FREE";

    public SheetParseResult<SheetProductRow> parseProducts(List<List<Object>> rawRows) {
        return parseRows(rawRows, (columns, rowNumber) -> new SheetProductRow(
                requireText(columns, ProductSheetHeaders.CODE, "상품 코드"),
                requireText(columns, ProductSheetHeaders.NAME, "상품명"),
                getText(columns, ProductSheetHeaders.CATEGORY),
                getText(columns, ProductSheetHeaders.GENDER),
                requireInt(columns, ProductSheetHeaders.PRICE, "가격"),
                getText(columns, ProductSheetHeaders.FIT),
                getText(columns, ProductSheetHeaders.DESCRIPTION),
                getText(columns, ProductSheetHeaders.FABRICATION),
                getText(columns, ProductSheetHeaders.COLOR)
        ));
    }

    public SheetParseResult<SheetMaterialRow> parseMaterials(List<List<Object>> rawRows) {
        return parseRows(rawRows, (columns, rowNumber) -> new SheetMaterialRow(
                requireText(columns, MaterialSheetHeaders.PRODUCT_CODE, "상품 코드"),
                requireText(columns, MaterialSheetHeaders.NAME, "소재명"),
                requireBigDecimal(columns, MaterialSheetHeaders.PERCENT, "혼용률")
        ));
    }

    public SheetParseResult<SheetSizeMeasurementRow> parseSizeMeasurements(List<List<Object>> rawRows) {
        return parseRows(rawRows, (columns, rowNumber) -> new SheetSizeMeasurementRow(
                requireText(columns, SizeMeasurementSheetHeaders.PRODUCT_CODE, "상품 코드"),
                normalizeSizeLabel(getText(columns, SizeMeasurementSheetHeaders.SIZE)),
                getText(columns, SizeMeasurementSheetHeaders.PART),
                requireText(columns, SizeMeasurementSheetHeaders.MEASUREMENT_NAME, "측정항목"),
                requireBigDecimal(columns, SizeMeasurementSheetHeaders.VALUE, "값"),
                getText(columns, SizeMeasurementSheetHeaders.UNIT)
        ));
    }

    private <T> SheetParseResult<T> parseRows(List<List<Object>> rawRows, RowMapper<T> rowMapper) {
        List<T> validRows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();

        if (rawRows == null || rawRows.size() < 2) {
            return new SheetParseResult<>(validRows, errors);
        }

        Map<String, Integer> headerIndex = buildHeaderIndex(rawRows.get(0));

        for (int i = 1; i < rawRows.size(); i++) {
            int rowNumber = i + 1;
            RowColumns columns = new RowColumns(headerIndex, rawRows.get(i));
            try {
                validRows.add(rowMapper.map(columns, rowNumber));
            } catch (RowSkipException e) {
                errors.add(new RowError(rowNumber, e.getMessage()));
            }
        }

        return new SheetParseResult<>(validRows, errors);
    }

    private Map<String, Integer> buildHeaderIndex(List<Object> headerRow) {
        Map<String, Integer> headerIndex = new HashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            Object header = headerRow.get(i);
            if (header != null) {
                headerIndex.put(String.valueOf(header).trim(), i);
            }
        }
        return headerIndex;
    }

    private String normalizeSizeLabel(String rawSizeLabel) {
        return (rawSizeLabel == null || rawSizeLabel.isBlank()) ? FREE_SIZE_LABEL : rawSizeLabel;
    }

    private String getText(RowColumns columns, String header) {
        String value = columns.get(header);
        return (value == null || value.isBlank()) ? null : value;
    }

    private String requireText(RowColumns columns, String header, String label) {
        String value = getText(columns, header);
        if (value == null) {
            throw new RowSkipException(label + "이(가) 없습니다.");
        }
        return value;
    }

    private int requireInt(RowColumns columns, String header, String label) {
        String raw = requireText(columns, header, label);
        String digitsOnly = raw.replaceAll("[^0-9-]", "");
        try {
            return Integer.parseInt(digitsOnly);
        } catch (NumberFormatException e) {
            throw new RowSkipException(label + " 형식이 올바르지 않습니다: " + raw);
        }
    }

    private BigDecimal requireBigDecimal(RowColumns columns, String header, String label) {
        String raw = requireText(columns, header, label);
        String numericOnly = raw.replaceAll("[^0-9.-]", "");
        try {
            return new BigDecimal(numericOnly);
        } catch (NumberFormatException e) {
            throw new RowSkipException(label + " 형식이 올바르지 않습니다: " + raw);
        }
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(RowColumns columns, int rowNumber);
    }

    private static final class RowColumns {
        private final Map<String, Integer> headerIndex;
        private final List<Object> row;

        private RowColumns(Map<String, Integer> headerIndex, List<Object> row) {
            this.headerIndex = headerIndex;
            this.row = row;
        }

        private String get(String header) {
            Integer index = headerIndex.get(header);
            if (index == null || index >= row.size()) {
                return null;
            }
            Object value = row.get(index);
            return value == null ? null : String.valueOf(value).trim();
        }
    }

    private static final class RowSkipException extends RuntimeException {
        private RowSkipException(String message) {
            super(message);
        }
    }
}
