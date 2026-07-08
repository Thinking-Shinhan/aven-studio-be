package kr.co.daeun.avenstudiobe.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "로그인 요청")
public class LoginReqDto {
    @NotBlank(message = "아이디를 입력해주세요.")
    @Schema(description = "로그인 ID", example = "aven01")
    private String loginId;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Schema(description = "비밀번호", example = "Test1234!")
    private String password;
}
