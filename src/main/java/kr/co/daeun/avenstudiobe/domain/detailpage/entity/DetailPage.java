package kr.co.daeun.avenstudiobe.domain.detailpage.entity;

import jakarta.persistence.*;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "detail_pages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DetailPage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "status", length = 20)
    private String status;

    @JoinColumn(name = "product_id", nullable = false, unique = true)
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "object_name", length = 500)
    private String objectName;

    @Column(name = "original_filename", length = 500)
    private String originalFilename;

    @Column(name = "size")
    private Long size;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    public static DetailPage create(
            Product product,
            String status,
            String fileUrl,
            String objectName,
            String originalFilename,
            Long size
    ) {
        DetailPage detailPage = new DetailPage();
        detailPage.product = product;
        detailPage.update(status, fileUrl, objectName, originalFilename, size);
        return detailPage;
    }

    public void update(
            String status,
            String fileUrl,
            String objectName,
            String originalFilename,
            Long size
    ) {
        this.status = status;
        this.fileUrl = fileUrl;
        this.objectName = objectName;
        this.originalFilename = originalFilename;
        this.size = size;
    }
}
