package kr.co.daeun.avenstudiobe.domain.detailpage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.CopyReviewRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.service.CopyReviewService;
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
@Tag(name = "Detail Page Copy Review", description = "상세페이지 AI 문구 검수 API")
public class CopyReviewController {

    private final CopyReviewService copyReviewService;

    @PostMapping("/review-copy")
    @Operation(summary = "상세페이지 AI 문구 검수")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문구 검수 성공"),
            @ApiResponse(responseCode = "400", description = "요청 형식·슬롯 정보 오류 또는 상품/asset 접근 불가"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "502", description = "이미지 처리 또는 AI 응답 오류"),
            @ApiResponse(responseCode = "503", description = "AI 서비스를 일시적으로 사용할 수 없음")
    })
    public ResponseEntity<CopyReviewRespDto> reviewCopy(
            @Valid @RequestBody CopyReviewReqDto request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(copyReviewService.review(request, authentication));
    }
}
