package kr.co.daeun.avenstudiobe.domain.sheet.dto;

import kr.co.daeun.avenstudiobe.domain.sheet.entity.SheetConnection;
import kr.co.daeun.avenstudiobe.domain.sheet.entity.SyncStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class SheetConnectionRespDto {

    private Long id;
    private String url;
    private String spreadsheetId;
    private SyncStatus syncStatus;
    private LocalDateTime lastSyncedAt;

    public static SheetConnectionRespDto from(SheetConnection sheetConnection) {
        return new SheetConnectionRespDto(
                sheetConnection.getId(),
                sheetConnection.getUrl(),
                sheetConnection.getSpreadsheetId(),
                sheetConnection.getSyncStatus(),
                sheetConnection.getLastSyncedAt()
        );
    }
}