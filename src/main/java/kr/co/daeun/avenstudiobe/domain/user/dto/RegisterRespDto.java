package kr.co.daeun.avenstudiobe.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RegisterRespDto {
    private Long userId;
    private String loginId;
    private String name;
    private Long brandId;
    private String brandName;
}
