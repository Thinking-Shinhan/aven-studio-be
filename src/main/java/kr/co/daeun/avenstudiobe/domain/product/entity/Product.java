package kr.co.daeun.avenstudiobe.domain.product.entity;

import jakarta.persistence.*;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@NoArgsConstructor
@Getter
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "gender", length = 1)
    private String gender;

    @Column(name = "color", length = 30)
    private String color;

    @Column(name = "price")
    private int price;

    @Column(name = "fit_description")
    private String fitDescription;

    @Column(name = "description")
    private String description;

    @Column(name = "fabrication_description")
    private String fabricationDescription;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @Column(name = "lining")
    private int lining;

    @Column(name = "elasticity")
    private int elasticity;

    @Column(name = "transparency")
    private int transparency;

    @Column(name = "touch_feeling")
    private int touchFeeling;

    @Column(name = "thickness")
    private int thickness;

    public static Product create(
            Brand brand,
            String code,
            String name,
            String category,
            String gender,
            String color,
            int price,
            String fitDescription,
            String description,
            String fabricationDescription,
            int lining,
            int elasticity,
            int transparency,
            int touch_feeling,
            int thickness
    ) {
        Product product = new Product();
        product.brand = brand;
        product.code = code;
        product.update(name, category, gender, color, price, fitDescription, description, fabricationDescription, lining, elasticity, transparency, touch_feeling, thickness);
        return product;
    }

    public void update(
            String name,
            String category,
            String gender,
            String color,
            int price,
            String fitDescription,
            String description,
            String fabricationDescription,
            int lining,
            int elasticity,
            int transparency,
            int touchFeeling,
            int thickness
    ) {
        this.name = name;
        this.category = category;
        this.gender = gender;
        this.color = color;
        this.price = price;
        this.fitDescription = fitDescription;
        this.description = description;
        this.fabricationDescription = fabricationDescription;
        this.lining = lining;
        this.elasticity = elasticity;
        this.transparency = transparency;
        this.touchFeeling = touchFeeling;
        this.thickness = thickness;
    }
}