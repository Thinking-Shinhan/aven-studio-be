package kr.co.daeun.avenstudiobe.domain.sheet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Google Sheet 연결 요청")
public class SheetConnectionReqDto {
    @NotBlank(message = "스프레드시트 URL은 필수입니다.")
    @Schema(description = "연결할 Google Sheet URL", example = "https://docs.google.com/spreadsheets/d/1abcDEFghiJKLmnopQRstuVWxyz/edit")
    private String url;
}
