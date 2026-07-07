package kr.co.daeun.avenstudiobe.domain.auth.controller;

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
public class AuthController {

    private final UsersService usersService;
    private final AuthService authService;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @PostMapping("/register")
    public ResponseEntity<RegisterRespDto> register(@Valid @RequestBody RegisterReqDto dto) {
        RegisterRespDto registerRespDto = usersService.register(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(registerRespDto);
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@RequestBody @Valid LoginReqDto dto,
                                      HttpServletResponse response) {
        TokenDto tokens = authService.login(dto);

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", tokens.getAccessToken())
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(accessExpiration/1000)
                .sameSite("Strict")
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokens.getRefreshToken())
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(refreshExpiration/1000)
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
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
                .secure(false)
                .path("/")
                .maxAge(accessExpiration/1000)
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                       HttpServletResponse response) {
        authService.logout(request);

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Strict")
                .maxAge(0)
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Strict")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ResponseEntity.ok().build();
    }
}