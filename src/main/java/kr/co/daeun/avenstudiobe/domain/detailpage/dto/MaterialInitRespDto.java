package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class MaterialInitRespDto {
    private Long id;
    private String name;
    private BigDecimal percent;
}
