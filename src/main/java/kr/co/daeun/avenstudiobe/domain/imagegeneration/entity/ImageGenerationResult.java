package kr.co.daeun.avenstudiobe.domain.imagegeneration.entity;

import jakarta.persistence.*;
import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "image_generation_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageGenerationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private ImageGenerationJob job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(name = "shot_template_id")
    private Long shotTemplateId;

    @Column(name = "output_index", nullable = false)
    private Integer outputIndex;

    @Column(name = "product_view", length = 50)
    private String productView;

    @Column(nullable = false, length = 30)
    private String status;

    @Column
    private BigDecimal score;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ImageGenerationResult generated(
            ImageGenerationJob job,
            Asset asset,
            int outputIndex
    ) {
        ImageGenerationResult result = new ImageGenerationResult();
        result.job = job;
        result.asset = asset;
        result.outputIndex = outputIndex;
        result.status = "GENERATED";
        return result;
    }

    public static ImageGenerationResult generatedWithoutSave(
            ImageGenerationJob job,
            int outputIndex
    ) {
        ImageGenerationResult result = new ImageGenerationResult();
        result.job = job;
        result.outputIndex = outputIndex;
        result.status = "GENERATED_NOT_SAVED";
        return result;
    }
}
