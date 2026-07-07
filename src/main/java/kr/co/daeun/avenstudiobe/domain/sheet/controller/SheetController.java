package kr.co.daeun.avenstudiobe.domain.sheet.controller;

import jakarta.validation.Valid;
import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetConnectionReqDto;
import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetConnectionRespDto;
import kr.co.daeun.avenstudiobe.domain.sheet.service.SheetSyncService;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import kr.co.daeun.avenstudiobe.domain.user.service.UsersService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(
        name = "google.sheets.enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
@RequestMapping("/sheets")
public class SheetController {

    private final SheetSyncService sheetSyncService;
    private final UsersService usersService;

    @PostMapping
    public ResponseEntity<SheetConnectionRespDto> connect(
            @Valid @RequestBody SheetConnectionReqDto request,
            Authentication authentication
    ) {
        User user = usersService.findByLoginId(authentication.getName());

        SheetConnectionRespDto response = sheetSyncService.connect(user.getBrand(), request.getUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
