package kr.co.daeun.avenstudiobe.domain.asset.entity;

import jakarta.persistence.*;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Asset {

    private static final String DELETED_STATUS = "DELETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(name = "asset_role", length = 30)
    private String assetRole;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    /* GCS 내부 객체 경로 */
    @Column(name = "object_name", nullable = false, length = 500)
    private String objectName;

    /* 사용자가 업로드한 원본 파일명 */
    @Column(name = "original_filename")
    private String originalFilename;

    /* image/jpeg, image/png, image/webp */
    @Column(name = "content_type", length = 100)
    private String contentType;

    /* byte */
    @Column(name = "size")
    private Long size;

    @Column(columnDefinition = "TEXT")
    private String prompt;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    public void markDeleted() {
        this.status = DELETED_STATUS;
    }

}
