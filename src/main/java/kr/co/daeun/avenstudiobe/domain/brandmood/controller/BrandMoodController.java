package kr.co.daeun.avenstudiobe.domain.brandmood.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.co.daeun.avenstudiobe.domain.brandmood.dto.BrandMoodAnalyzeReqDto;
import kr.co.daeun.avenstudiobe.domain.brandmood.dto.BrandMoodAnalysisRespDto;
import kr.co.daeun.avenstudiobe.domain.brandmood.dto.BrandMoodRespDto;
import kr.co.daeun.avenstudiobe.domain.brandmood.dto.BrandMoodSaveReqDto;
import kr.co.daeun.avenstudiobe.domain.brandmood.service.BrandMoodService;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import kr.co.daeun.avenstudiobe.domain.user.service.UsersService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/brands/me/moods")
@Tag(name = "Brand Moods", description = "브랜드 무드 분석 및 저장 API")
@SecurityRequirement(name = "accessTokenCookie")
public class BrandMoodController {

    private final BrandMoodService brandMoodService;
    private final UsersService usersService;

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "브랜드 무드 분석",
            description = "브랜드 URL과 선택 PDF를 분석합니다. 최상위 필드는 사용자 확인용 표시값이며, "
                    + "brandName은 로그인 사용자의 브랜드에서 조회합니다. rawAnalysisJson에는 이미지 생성용 상세 분석과 "
                    + "표시값이 함께 포함됩니다. DB에는 저장하지 않습니다."
    )
    public ResponseEntity<BrandMoodAnalysisRespDto> analyze(
            @RequestParam("sourceUrl") String sourceUrl,
            @RequestParam(required = false) String sourceText,
            @RequestParam(required = false) String customInstruction,
            @RequestPart(required = false) MultipartFile file,
            Authentication authentication
    ) {
        User user = usersService.findByLoginId(authentication.getName());

        BrandMoodAnalyzeReqDto request = new BrandMoodAnalyzeReqDto();
        request.setSourceUrl(sourceUrl);
        request.setSourceText(sourceText);
        request.setCustomInstruction(customInstruction);

        BrandMoodAnalysisRespDto response = brandMoodService.analyze(request, file, user);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(
            summary = "브랜드 무드 저장",
            description = "사용자가 확인 또는 수정한 표시값을 저장하고, rawAnalysisJson의 상세 분석과 조합해 "
                    + "이미지 생성용 브랜드 지시문을 서버에서 생성합니다."
    )
    public ResponseEntity<BrandMoodRespDto> save(
            @Valid @RequestBody BrandMoodSaveReqDto request,
            Authentication authentication
    ) {
        User user = usersService.findByLoginId(authentication.getName());
        BrandMoodRespDto response = brandMoodService.save(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/current")
    @Operation(summary = "현재 브랜드 무드 조회", description = "로그인 사용자 브랜드의 활성 최신 브랜드 무드를 조회합니다.")
    public ResponseEntity<BrandMoodRespDto> get(
            Authentication authentication
    ) {
        User user = usersService.findByLoginId(authentication.getName());
        BrandMoodRespDto response = brandMoodService.getCurrent(user);
        return ResponseEntity.ok(response);
    }
}
