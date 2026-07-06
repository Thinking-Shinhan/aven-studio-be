package kr.co.daeun.avenstudiobe.domain.product.dto;

import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ProductRespDto {
    private Long id;
    private String code;
    private String name;
    private String category;
    private String gender;
    private String color;
    private int price;
    private LocalDateTime createdAt;

    public static ProductRespDto from(Product product) {
        return new ProductRespDto(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getCategory(),
                product.getGender(),
                product.getColor(),
                product.getPrice(),
                product.getCreatedAt()
        );
    }
}