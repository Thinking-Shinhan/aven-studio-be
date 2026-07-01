package kr.co.daeun.avenstudiobe.domain.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import kr.co.daeun.avenstudiobe.domain.auth.dto.LoginReqDto;
import kr.co.daeun.avenstudiobe.domain.auth.dto.TokenDto;
import kr.co.daeun.avenstudiobe.domain.users.entity.Users;
import kr.co.daeun.avenstudiobe.domain.auth.entity.RefreshToken;
import kr.co.daeun.avenstudiobe.domain.auth.repository.RefreshTokenRepository;
import kr.co.daeun.avenstudiobe.domain.users.repository.UsersRepository;
import kr.co.daeun.avenstudiobe.domain.auth.security.JwtUtil;
import kr.co.daeun.avenstudiobe.global.exception.ExpiredRefreshTokenException;
import kr.co.daeun.avenstudiobe.global.exception.InvalidLoginException;
import kr.co.daeun.avenstudiobe.global.exception.InvalidTokenException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsersRepository usersRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public TokenDto login(LoginReqDto dto) {
        Users user = usersRepository.findByLoginId(dto.getLoginId())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 틀렸습니다."));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new InvalidLoginException();
        }

        String accessToken = jwtUtil.generateAccessToken(user.getLoginId());


        String refreshToken = jwtUtil.generateRefreshToken(user.getLoginId());
        LocalDateTime refreshTokenExpiresAt = jwtUtil.getRefreshTokenExpiresAt();

        RefreshToken savedRefreshToken = refreshTokenRepository.findByUser(user)
                .map(token -> {
                    token.updateToken(refreshToken,  refreshTokenExpiresAt);
                    return token;
                })
                .orElseGet(() -> new RefreshToken(user, refreshToken,  refreshTokenExpiresAt));

        refreshTokenRepository.save(savedRefreshToken);

        return new TokenDto(accessToken, refreshToken);
    }

    @Transactional
    public String refresh(String refreshToken) {

        RefreshToken saved = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new InvalidTokenException());

        if (saved.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(saved);
            throw new ExpiredRefreshTokenException();
        }

        return jwtUtil.generateAccessToken(saved.getUser().getLoginId());
    }

    @Transactional
    public void logout(HttpServletRequest request) {
        if (request.getCookies() == null) return;

        String refreshToken = Arrays.stream(request.getCookies())
                .filter(cookie -> "refreshToken".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);

        if (refreshToken == null) return;

        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(refreshTokenRepository::delete);
    }
}
