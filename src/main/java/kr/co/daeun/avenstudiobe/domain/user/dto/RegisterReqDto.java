package kr.co.daeun.avenstudiobe.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterReqDto {

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9]{4,20}$", message = "아이디는 영문과 숫자 4~20자여야 합니다.")
    @NotBlank
    private String loginId;

    @NotBlank
    @Size(min = 2, max = 20)
    private String name;

    @NotBlank
    @NotBlank
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+=\\-]).{8,}$",
            message = "비밀번호는 8자 이상이며 영문, 숫자, 특수문자를 포함해야 합니다.")
    private String password;

    @NotBlank
    private String passwordConfirm;

    @NotBlank
    private String brandName;
}
