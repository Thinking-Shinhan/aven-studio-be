package kr.co.daeun.avenstudiobe.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "회원가입 요청")
public class RegisterReqDto {

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9]{4,20}$", message = "아이디는 영문과 숫자 4~20자여야 합니다.")
    @NotBlank
    @Schema(description = "로그인 ID입니다. 영문과 숫자 4~20자를 사용합니다.", example = "aven01")
    private String loginId;

    @NotBlank
    @Size(min = 2, max = 20)
    @Schema(description = "사용자 이름입니다. 2~20자를 사용합니다.", example = "이다은")
    private String name;

    @NotBlank
    @NotBlank
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+=\\-]).{8,}$",
            message = "비밀번호는 8자 이상이며 영문, 숫자, 특수문자를 포함해야 합니다.")
    @Schema(description = "비밀번호입니다. 영문, 숫자, 특수문자를 포함해야 합니다.", example = "Test1234!")
    private String password;

    @NotBlank
    @Schema(description = "비밀번호 확인 값입니다.", example = "Test1234!")
    private String passwordConfirm;

    @NotBlank
    @Schema(description = "사용자가 소속될 브랜드명입니다.", example = "Aven")
    private String brandName;
}
