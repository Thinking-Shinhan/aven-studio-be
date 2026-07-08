package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;


@Getter
@AllArgsConstructor
public class SizeInitRespDto {
    private Long id;
    private String size;
    private List<MeasurementInitRespDto> measurements;
}
