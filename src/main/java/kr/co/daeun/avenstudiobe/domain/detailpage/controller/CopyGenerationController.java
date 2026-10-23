package kr.co.daeun.avenstudiobe.domain.detailpage.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerateReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyGenerateRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.service.CopyGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/detail-pages")
@Tag(name = "Detail Page Copy Generation", description = "상세페이지 문구 생성 API")
public class CopyGenerationController {

    private final CopyGenerationService copyGenerationService;

    @PostMapping("/generate-copy")
    @Operation(summary = "상세페이지 문구 생성", description = "빈 문구 슬롯의 카피를 AI로 생성합니다. AI 장애 또는 검증 실패 시 재시도 가능한 오류를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문구 생성 성공"),
            @ApiResponse(responseCode = "400", description = "요청 형식·슬롯 정보가 올바르지 않거나 상품/asset 접근이 허용되지 않음"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "502", description = "AI 응답 형식 또는 upstream 응답 오류"),
            @ApiResponse(responseCode = "503", description = "AI 서비스를 일시적으로 사용할 수 없음")
    })
    public ResponseEntity<CopyGenerateRespDto> generateCopy(
            @Valid @RequestBody CopyGenerateReqDto request,
            Authentication authentication
    ) {
        CopyGenerateRespDto response = copyGenerationService.generate(request, authentication);
        return ResponseEntity.ok(response);
    }
}
