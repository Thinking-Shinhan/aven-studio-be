package kr.co.daeun.avenstudiobe.domain.imagegeneration.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "reference_assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReferenceAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_type", nullable = false, length = 30)
    private String referenceType;

    @Column(name = "reference_key", nullable = false, length = 100)
    private String referenceKey;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "object_name", length = 500)
    private String objectName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

<<<<<<< HEAD
=======
    @Column(name = "cut_type", length = 30)
    private String cutType;

>>>>>>> develop
    @Column(name = "prompt_text", columnDefinition = "TEXT")
    private String promptText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ReferenceAsset(
            String referenceType,
            String referenceKey,
            String name,
            String imageUrl,
            String objectName,
            String contentType,
            String description,
            Boolean active,
            Integer sortOrder,
<<<<<<< HEAD
=======
            String cutType,
>>>>>>> develop
            String promptText
    ) {
        this.referenceType = referenceType;
        this.referenceKey = referenceKey;
        this.name = name;
        this.imageUrl = imageUrl;
        this.objectName = objectName;
        this.contentType = contentType;
        this.description = description;
        this.active = active;
        this.sortOrder = sortOrder;
<<<<<<< HEAD
=======
        this.cutType = cutType;
>>>>>>> develop
        this.promptText = promptText;
    }
}
