package kr.co.daeun.avenstudiobe.domain.product.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ProductListRespDto {
    private String category;
    private String color;
    private String gender;
    private String keyword;
    private List<ProductRespDto> products;
}
