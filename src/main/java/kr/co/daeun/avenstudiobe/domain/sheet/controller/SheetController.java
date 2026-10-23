package kr.co.daeun.avenstudiobe.domain.sheet.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetConnectionReqDto;
import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetConnectionRespDto;
import kr.co.daeun.avenstudiobe.domain.sheet.service.SheetSyncService;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import kr.co.daeun.avenstudiobe.domain.user.service.UsersService;
import kr.co.daeun.avenstudiobe.global.exception.ErrorResponse;
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
@Tag(name = "Sheets", description = "Google Sheets 연결 및 동기화 API")
@SecurityRequirement(name = "accessTokenCookie")
public class SheetController {

    private final SheetSyncService sheetSyncService;
    private final UsersService usersService;

    @PostMapping
    @Operation(summary = "Google Sheet 연결", description = "브랜드에 Google Sheet를 연결하고 상품 데이터를 처음으로 동기화합니다. 이후 스케줄러를 통해 주기적으로 동기화.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "시트 연결 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "시트 URL 또는 요청이 올바르지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 연결된 시트이거나 동기화가 진행 중임",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<SheetConnectionRespDto> connect(
            @Valid @RequestBody SheetConnectionReqDto request,
            Authentication authentication
    ) {
        User user = usersService.findByLoginId(authentication.getName());

        SheetConnectionRespDto response = sheetSyncService.connect(user.getBrand(), request.getUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
