package kr.co.daeun.avenstudiobe.domain.sheet.controller;

import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetConnectionRespDto;
import kr.co.daeun.avenstudiobe.domain.sheet.repository.SheetConnectionRepository;
import kr.co.daeun.avenstudiobe.domain.sheet.service.SheetSyncService;
import kr.co.daeun.avenstudiobe.global.exception.SheetConnectionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(
        name = "google.sheets.enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
@RequestMapping("/sheets") // 개발용 수동 동기화
public class SheetSyncController {

    private final SheetSyncService sheetSyncService;
    private final SheetConnectionRepository sheetConnectionRepository;

    @PostMapping("/{connectionId}/sync")
    public ResponseEntity<SheetConnectionRespDto> sync(@PathVariable Long connectionId) {
        sheetSyncService.sync(connectionId);

        SheetConnectionRespDto response = sheetConnectionRepository.findById(connectionId)
                .map(SheetConnectionRespDto::from)
                .orElseThrow(SheetConnectionNotFoundException::new);

        return ResponseEntity.ok(response);
    }
}
