package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ProductInitRespDto {
    private Long id;
    private String code;
    private String name;
    private String category;
    private String gender;
    private String color;
    private int price;
    private String fitDescription;
    private String description;
    private String fabricationDescription;
    private int lining;
    private int elasticity;
    private int transparency;
    private int touchFeeling;
    private int thickness;

    private List<MaterialInitRespDto> materials;
    private List<SizeInitRespDto> sizes;
}
