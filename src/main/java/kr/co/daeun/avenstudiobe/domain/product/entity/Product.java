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
            String fabricationDescription
    ) {
        Product product = new Product();
        product.brand = brand;
        product.code = code;
        product.update(name, category, gender, color, price, fitDescription, description, fabricationDescription);
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
            String fabricationDescription
    ) {
        this.name = name;
        this.category = category;
        this.gender = gender;
        this.color = color;
        this.price = price;
        this.fitDescription = fitDescription;
        this.description = description;
        this.fabricationDescription = fabricationDescription;
    }
}