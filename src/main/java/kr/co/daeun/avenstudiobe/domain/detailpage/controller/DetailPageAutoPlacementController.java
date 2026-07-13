package kr.co.daeun.avenstudiobe.domain.detailpage.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.AutoImagePlacementRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.service.AutoImagePlacementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/detail-pages")
public class DetailPageAutoPlacementController {

    private final AutoImagePlacementService autoImagePlacementService;

    @PostMapping({"/auto-place-image"})
    @Operation(summary = "상세페이지 이미지 자동 배치", description = "AI 배치를 우선 적용하고, 실패하거나 비어 있는 슬롯은 deterministic fallback으로 보완합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "이미지 배치 결과 반환. AI 일부 성공 시 fallback 결과가 함께 포함될 수 있음"),
            @ApiResponse(responseCode = "400", description = "요청 형식이 올바르지 않음"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "409", description = "배치 가능한 이미지가 없음")
    })
    public ResponseEntity<AutoImagePlacementRespDto> autoPlace(
            @Valid @RequestBody AutoImagePlacementReqDto request
    ) {
        AutoImagePlacementRespDto response = autoImagePlacementService.autoPlace(request);
        return ResponseEntity.ok(response);
    }
}
