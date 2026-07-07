package kr.co.daeun.avenstudiobe.domain.sheet.service;

import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.brand.repository.BrandRepository;
import kr.co.daeun.avenstudiobe.domain.product.service.ProductSheetSyncWriter;
import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetConnectionRespDto;
import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetMaterialRow;
import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetParseResult;
import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetProductRow;
import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetSizeMeasurementRow;
import kr.co.daeun.avenstudiobe.domain.sheet.entity.SheetConnection;
import kr.co.daeun.avenstudiobe.domain.sheet.entity.SyncStatus;
import kr.co.daeun.avenstudiobe.domain.sheet.mapper.MaterialSheetHeaders;
import kr.co.daeun.avenstudiobe.domain.sheet.mapper.ProductSheetHeaders;
import kr.co.daeun.avenstudiobe.domain.sheet.mapper.SheetRowParser;
import kr.co.daeun.avenstudiobe.domain.sheet.mapper.SizeMeasurementSheetHeaders;
import kr.co.daeun.avenstudiobe.domain.sheet.repository.SheetConnectionRepository;
import kr.co.daeun.avenstudiobe.global.exception.BrandNotFoundException;
import kr.co.daeun.avenstudiobe.global.exception.SheetAccessDeniedException;
import kr.co.daeun.avenstudiobe.global.exception.SheetAlreadyConnectedException;
import kr.co.daeun.avenstudiobe.global.exception.SheetConnectionNotFoundException;
import kr.co.daeun.avenstudiobe.global.exception.SheetReadException;
import kr.co.daeun.avenstudiobe.global.exception.SheetSyncInProgressException;
import kr.co.daeun.avenstudiobe.integration.google.GoogleSheetUrlParser;
import kr.co.daeun.avenstudiobe.integration.google.GoogleSheetsReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(
        name = "google.sheets.enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class SheetSyncService {

    private static final String SHEET_RANGE_SUFFIX = "!A:Z";

    private final SheetConnectionRepository sheetConnectionRepository;
    private final BrandRepository brandRepository;
    private final GoogleSheetsReader googleSheetsReader;
    private final SheetRowParser sheetRowParser;
    private final ProductSheetSyncWriter productSheetSyncWriter;

    public SheetConnectionRespDto connect(Brand brand, String url) {
        String spreadsheetId = GoogleSheetUrlParser.extractSpreadsheetId(url);
        SheetConnection sheetConnection = saveConnection(brand, url, spreadsheetId);

        sync(sheetConnection.getId());

        SheetConnection synced = sheetConnectionRepository.findById(sheetConnection.getId())
                .orElseThrow(SheetConnectionNotFoundException::new);
        return SheetConnectionRespDto.from(synced);
    }

    // 최초 연동/스케줄 동기화가 공용으로 쓰는 진입점이라 @Transactional을 걸지 않는다.
    // 아래에서 어떤 예외가 나든 markFailed까지는 반드시 커밋되어야 하기 때문.
    public void sync(Long connectionId) {
        if (sheetConnectionRepository.tryMarkInProgress(connectionId) == 0) {
            throw new SheetSyncInProgressException();
        }

        SheetConnection sheetConnection = sheetConnectionRepository.findById(connectionId)
                .orElseThrow(SheetConnectionNotFoundException::new);
        Brand brand = brandRepository.findById(sheetConnection.getBrand().getId())
                .orElseThrow(BrandNotFoundException::new);

        try {
            SyncOutcome outcome = runSync(brand, sheetConnection.getSpreadsheetId());
            if (outcome.hasIssues()) {
                sheetConnection.markPartialSuccess(outcome.describe());
            } else {
                sheetConnection.markSuccess(outcome.describe());
            }
        } catch (SheetAccessDeniedException | SheetReadException e) {
            // 구글 시트 자체에 접근할 수 없는 치명적 오류는 상태를 기록한 뒤 그대로 호출자에게 전파한다.
            // connect()로 호출된 경우 GlobalExceptionHandler가 403/404/502를 그대로 응답할 수 있게 하기 위함.
            sheetConnection.markFailed(e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("시트 동기화 실패: connectionId={}", connectionId, e);
            sheetConnection.markFailed(e.getMessage());
        } finally {
            sheetConnectionRepository.save(sheetConnection);
        }
    }

    // 브랜드당 시트 연동은 1개만 허용한다. 단, 이전 시도가 FAILED로 끝난 경우는
    // 실제로는 연동에 실패한 것이므로 재시도를 막지 않고 그 자리에서 재연동을 허용한다.
    @Transactional
    public SheetConnection saveConnection(Brand brand, String url, String spreadsheetId) {
        Optional<SheetConnection> existing = sheetConnectionRepository.findByBrandId(brand.getId());

        if (existing.isPresent()) {
            SheetConnection sheetConnection = existing.get();
            if (sheetConnection.getSyncStatus() != SyncStatus.FAILED) {
                throw new SheetAlreadyConnectedException();
            }
            sheetConnection.markConnected(url, spreadsheetId);
            return sheetConnectionRepository.save(sheetConnection);
        }

        try {
            return sheetConnectionRepository.save(SheetConnection.create(brand, url, spreadsheetId));
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 브랜드로 연동 요청이 들어와 brand_id 유니크 제약에 걸린 경우.
            // 500으로 새지 않도록 명세상의 409로 변환한다.
            throw new SheetAlreadyConnectedException();
        }
    }

    private SyncOutcome runSync(Brand brand, String spreadsheetId) {
        SheetParseResult<SheetProductRow> products = sheetRowParser.parseProducts(
                googleSheetsReader.read(spreadsheetId, toRange(ProductSheetHeaders.SHEET_NAME)));
        SheetParseResult<SheetMaterialRow> materials = sheetRowParser.parseMaterials(
                googleSheetsReader.read(spreadsheetId, toRange(MaterialSheetHeaders.SHEET_NAME)));
        SheetParseResult<SheetSizeMeasurementRow> measurements = sheetRowParser.parseSizeMeasurements(
                googleSheetsReader.read(spreadsheetId, toRange(SizeMeasurementSheetHeaders.SHEET_NAME)));

        Map<String, List<SheetMaterialRow>> materialsByProductCode = materials.validRows().stream()
                .collect(Collectors.groupingBy(SheetMaterialRow::productCode));
        Map<String, List<SheetSizeMeasurementRow>> measurementsByProductCode = measurements.validRows().stream()
                .collect(Collectors.groupingBy(SheetSizeMeasurementRow::productCode));

        int successCount = 0;
        List<String> failures = new ArrayList<>();

        for (SheetProductRow productRow : products.validRows()) {
            try {
                productSheetSyncWriter.applyProduct(
                        brand,
                        productRow,
                        materialsByProductCode.getOrDefault(productRow.code(), List.of()),
                        measurementsByProductCode.getOrDefault(productRow.code(), List.of())
                );
                successCount++;
            } catch (Exception e) {
                log.warn("상품 동기화 실패: code={}", productRow.code(), e);
                failures.add(productRow.code() + ": " + e.getMessage());
            }
        }

        int skippedRowCount = products.errors().size() + materials.errors().size() + measurements.errors().size();
        return new SyncOutcome(successCount, skippedRowCount, failures);
    }

    private String toRange(String sheetName) {
        return sheetName + SHEET_RANGE_SUFFIX;
    }

    private record SyncOutcome(int successCount, int skippedRowCount, List<String> failures) {
        private boolean hasIssues() {
            return skippedRowCount > 0 || !failures.isEmpty();
        }

        private String describe() {
            StringBuilder message = new StringBuilder()
                    .append(successCount).append("개 상품 동기화 완료");
            if (skippedRowCount > 0) {
                message.append(", 형식 오류로 건너뛴 행 ").append(skippedRowCount).append("개");
            }
            if (!failures.isEmpty()) {
                message.append(", 저장 실패 ").append(failures.size()).append("건: ")
                        .append(String.join("; ", failures));
            }
            return message.toString();
        }
    }
}
