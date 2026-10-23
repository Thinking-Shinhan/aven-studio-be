package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class MeasurementInitRespDto {
    private Long id;
    private String part;
    private String name;
    private BigDecimal value;
    private String unit;
}
