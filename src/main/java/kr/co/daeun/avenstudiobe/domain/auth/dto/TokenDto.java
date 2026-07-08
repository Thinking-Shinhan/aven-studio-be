package kr.co.daeun.avenstudiobe.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "발급된 JWT 토큰")
public class TokenDto {
    @Schema(description = "Access Token")
    private String accessToken;
    @Schema(description = "Refresh Token")
    private String refreshToken;
}
