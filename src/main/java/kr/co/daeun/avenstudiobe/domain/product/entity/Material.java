package kr.co.daeun.avenstudiobe.domain.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "materials")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Material {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal percent;

    @JoinColumn(name = "product_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    public static Material of(Product product, String name, BigDecimal percent) {
        Material material = new Material();
        material.product = product;
        material.name = name;
        material.percent = percent;
        return material;
    }
}
