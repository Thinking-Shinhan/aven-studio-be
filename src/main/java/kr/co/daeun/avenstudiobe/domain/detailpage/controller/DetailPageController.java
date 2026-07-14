package kr.co.daeun.avenstudiobe.domain.detailpage.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillReqDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageAutoFillRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageInitRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.DetailPageSaveRespDto;
import kr.co.daeun.avenstudiobe.domain.detailpage.service.DetailPageAutoFillService;
import kr.co.daeun.avenstudiobe.domain.detailpage.service.DetailPageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/detail-pages")
@Tag(name = "Detail Page", description = "상세페이지 제작 초기 데이터 조회, 자동 채우기, 저장 API")
public class DetailPageController {

    private final DetailPageService detailPageService;
    private final DetailPageAutoFillService detailPageAutoFillService;

    @GetMapping("/init")
    @Operation(summary = "상세페이지 초기 데이터 조회", description = "상품, 에셋, 사이즈, 소재 등 상세페이지 제작에 필요한 초기 데이터를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "초기 데이터 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음")
    })
    public ResponseEntity<DetailPageInitRespDto> getInitData(
            @Parameter(description = "상품 ID", example = "14") @RequestParam Long productId,
            Authentication authentication
    ) {
        DetailPageInitRespDto response = detailPageService.getInitData(productId, authentication);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/auto-fill")
    @Operation(summary = "상세페이지 자동 채우기", description = "템플릿 블록 배열을 기반으로 이미지와 문구를 AI로 자동 채웁니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "자동 채우기 성공"),
            @ApiResponse(responseCode = "400", description = "요청 형식이 올바르지 않음"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<DetailPageAutoFillRespDto> autoFill(
            @Valid @RequestBody DetailPageAutoFillReqDto request,
            Authentication authentication
    ) {
        DetailPageAutoFillRespDto response = detailPageAutoFillService.autoFill(request, authentication);
        return ResponseEntity.ok(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "상세페이지 이미지 저장", description = "완성된 상세페이지 이미지를 업로드하고 저장합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "요청 형식이 올바르지 않음"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "404", description = "상품을 찾을 수 없음")
    })
    public ResponseEntity<DetailPageSaveRespDto> save(
            @Parameter(description = "상품 ID", example = "1") @RequestParam Long productId,
            @Parameter(description = "상세페이지 이미지 파일") @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        DetailPageSaveRespDto response = detailPageService.saveDetailPageImage(productId, file, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
