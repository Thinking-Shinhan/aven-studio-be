package kr.co.daeun.avenstudiobe.domain.product.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sizes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Size {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "size", nullable = false, length = 30)
    private String size;

    @JoinColumn(name = "product_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    public static Size of(Product product, String size) {
        Size sizeEntity = new Size();
        sizeEntity.product = product;
        sizeEntity.size = size;
        return sizeEntity;
    }
}