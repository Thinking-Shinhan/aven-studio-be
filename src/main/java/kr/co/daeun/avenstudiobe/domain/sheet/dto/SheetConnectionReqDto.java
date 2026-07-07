package kr.co.daeun.avenstudiobe.domain.sheet.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SheetConnectionReqDto {
    @NotBlank(message = "스프레드시트 URL은 필수입니다.")
    private String url;
}
