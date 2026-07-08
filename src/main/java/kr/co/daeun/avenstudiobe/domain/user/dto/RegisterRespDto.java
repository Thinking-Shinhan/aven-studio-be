package kr.co.daeun.avenstudiobe.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "회원가입 응답")
public class RegisterRespDto {
    @Schema(description = "사용자 ID", example = "1")
    private Long userId;
    @Schema(description = "로그인 ID", example = "aven01")
    private String loginId;
    @Schema(description = "사용자 이름", example = "이다은")
    private String name;
    @Schema(description = "브랜드 ID", example = "1")
    private Long brandId;
    @Schema(description = "브랜드명", example = "Aven")
    private String brandName;
}
