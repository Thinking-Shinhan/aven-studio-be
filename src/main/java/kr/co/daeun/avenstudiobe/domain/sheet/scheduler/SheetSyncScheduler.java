package kr.co.daeun.avenstudiobe.domain.sheet.scheduler;

import kr.co.daeun.avenstudiobe.domain.sheet.entity.SheetConnection;
import kr.co.daeun.avenstudiobe.domain.sheet.entity.SyncStatus;
import kr.co.daeun.avenstudiobe.domain.sheet.repository.SheetConnectionRepository;
import kr.co.daeun.avenstudiobe.domain.sheet.service.SheetSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        name = "google.sheets.enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class SheetSyncScheduler {

    private final SheetConnectionRepository sheetConnectionRepository;
    private final SheetSyncService sheetSyncService;

    @Scheduled(fixedDelayString = "${sheet.sync.fixed-delay-ms}")
    public void syncConnectedSheets() {
        sheetConnectionRepository.findAll().stream()
                .filter(connection ->
                        connection.getSyncStatus() == SyncStatus.SUCCESS
                                || connection.getSyncStatus() == SyncStatus.PARTIAL_SUCCESS
                )
                .map(SheetConnection::getId)
                .forEach(this::syncOne);
    }

    private void syncOne(Long connectionId) {
        try {
            sheetSyncService.sync(connectionId);
        } catch (Exception e) {
            log.warn("예약 시트 동기화 실패: connectionId={}", connectionId, e);
        }
    }
}