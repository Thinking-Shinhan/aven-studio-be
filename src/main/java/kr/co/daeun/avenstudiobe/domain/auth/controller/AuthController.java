package kr.co.daeun.avenstudiobe.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kr.co.daeun.avenstudiobe.domain.auth.dto.LoginReqDto;
import kr.co.daeun.avenstudiobe.domain.auth.dto.TokenDto;
import kr.co.daeun.avenstudiobe.domain.user.dto.RegisterReqDto;
import kr.co.daeun.avenstudiobe.domain.user.dto.RegisterRespDto;
import kr.co.daeun.avenstudiobe.domain.user.service.UsersService;
import kr.co.daeun.avenstudiobe.domain.auth.service.AuthService;
import kr.co.daeun.avenstudiobe.global.exception.ErrorResponse;
import kr.co.daeun.avenstudiobe.global.exception.InvalidTokenException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 및 회원가입 API")
public class AuthController {

    private final UsersService usersService;
    private final AuthService authService;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @PostMapping("/register")
    @Operation(summary = "회원가입", description = "사용자 계정을 생성하고, 브랜드가 없으면 함께 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 또는 중복된 로그인 ID",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<RegisterRespDto> register(@Valid @RequestBody RegisterReqDto dto) {
        RegisterRespDto registerRespDto = usersService.register(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(registerRespDto);
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "사용자를 인증하고 accessToken과 refreshToken을 HttpOnly 쿠키로 설정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공. 토큰은 Set-Cookie 헤더로 반환됩니다."),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 정보가 올바르지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<Void> login(@RequestBody @Valid LoginReqDto dto,
                                       HttpServletResponse response) {
        TokenDto tokens = authService.login(dto);

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", tokens.getAccessToken())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(accessExpiration/1000)
                .sameSite("None")
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokens.getRefreshToken())
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(refreshExpiration/1000)
                .sameSite("None")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Access Token 재발급", description = "refreshToken 쿠키를 사용해 새로운 accessToken 쿠키를 발급합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access Token 재발급 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "refreshToken이 없거나 유효하지 않거나 만료됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<Void> refresh(HttpServletRequest request,
                                         HttpServletResponse response) {
        if (request.getCookies() == null) {
            throw new InvalidTokenException();
        }

        String refreshToken = Arrays.stream(request.getCookies())
                .filter(c -> "refreshToken".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow(() -> new InvalidTokenException("유효하지 않은 Refresh Token입니다."));

        String newAccessToken = authService.refresh(refreshToken);

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", newAccessToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(accessExpiration/1000)
                .sameSite("None")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "저장된 refreshToken을 삭제하고 인증 쿠키를 비웁니다.")
    @ApiResponse(responseCode = "200", description = "로그아웃 성공")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                        HttpServletResponse response) {
        authService.logout(request);

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("None")
                .maxAge(0)
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .sameSite("None")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok().build();
    }
}
