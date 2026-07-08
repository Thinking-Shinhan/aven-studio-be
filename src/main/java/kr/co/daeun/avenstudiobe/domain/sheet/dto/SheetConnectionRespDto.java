package kr.co.daeun.avenstudiobe.domain.sheet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.co.daeun.avenstudiobe.domain.sheet.entity.SheetConnection;
import kr.co.daeun.avenstudiobe.domain.sheet.entity.SyncStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Schema(description = "Google Sheet 연결 응답")
public class SheetConnectionRespDto {

    @Schema(description = "PK용 ID", example = "1")
    private Long id;
    @Schema(description = "연결된 Google Sheet URL")
    private String url;
    @Schema(description = "Google 스프레드시트 ID", example = "1abcDEFghiJKLmnopQRstuVWxyz")
    private String spreadsheetId;
    @Schema(description = "현재 동기화 상태", example = "SUCCESS")
    private SyncStatus syncStatus;
    @Schema(description = "마지막 동기화 일시", example = "2026-07-08T12:30:00")
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
