package kr.co.daeun.avenstudiobe.domain.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "size_measurements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class SizeMeasurement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "part", length = 30)
    private String part;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "value", precision = 5, scale = 2)
    private BigDecimal value;

    @Column(name = "unit", length = 20)
    private String unit;

    @JoinColumn(name = "size_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Size size;

    public static SizeMeasurement of(Size size, String part, String name, BigDecimal value, String unit) {
        SizeMeasurement sizeMeasurement = new SizeMeasurement();
        sizeMeasurement.size = size;
        sizeMeasurement.part = part;
        sizeMeasurement.name = name;
        sizeMeasurement.value = value;
        sizeMeasurement.unit = unit;
        return sizeMeasurement;
    }
}