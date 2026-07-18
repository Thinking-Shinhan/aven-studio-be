package kr.co.daeun.avenstudiobe.domain.brand.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import kr.co.daeun.avenstudiobe.domain.brand.dto.BrandHomeRespDto;
import kr.co.daeun.avenstudiobe.domain.brand.service.BrandService;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import kr.co.daeun.avenstudiobe.domain.user.service.UsersService;
import kr.co.daeun.avenstudiobe.global.exception.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "accessTokenCookie")
@RequestMapping("/brands/me")
@Tag(name = "Brand Home", description = "랜딩 페이지")
public class BrandController {

    private final UsersService usersService;
    private final BrandService brandService;

    @GetMapping("/home")
    @Operation(summary = "브랜드 홈 조회", description = "브랜드 분석 완료 여부(+정보), 시트 동기화 완료 여부(+정보)를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "브랜드 홈 조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<BrandHomeRespDto> getHomePage(Authentication authentication) {
        User user = usersService.findByLoginId(authentication.getName());
        return ResponseEntity.ok(brandService.getHomePage(user));
    }

}
