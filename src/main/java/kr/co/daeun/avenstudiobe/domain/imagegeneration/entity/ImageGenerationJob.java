package kr.co.daeun.avenstudiobe.domain.imagegeneration.entity;

import jakarta.persistence.*;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "image_generation_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageGenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "brand_mood_id", nullable = false)
    private Long brandMoodId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "cut_type", nullable = false, length = 30)
    private String cutType;

    @Column(name = "generation_mode", nullable = false, length = 30)
    private String generationMode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "negative_prompt", columnDefinition = "TEXT")
    private String negativePrompt;

    @Column(name = "user_options_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String userOptionsJson;

    @Column(name = "reference_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String referenceJson;

    @Column(name = "ai_name", length = 100)
    private String aiName;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "requested_count", nullable = false)
    private Integer requestedCount;

    @Column(name = "output_count", nullable = false)
    private Integer outputCount;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static ImageGenerationJob create(
            Brand brand,
            Product product,
            Long brandMoodId,
            User user,
            String cutType,
            String generationMode,
            String prompt,
            String negativePrompt,
            String userOptionsJson,
            String referenceJson,
            String aiName,
            int requestedCount
    ) {
        ImageGenerationJob job = new ImageGenerationJob();
        job.brand = brand;
        job.product = product;
        job.brandMoodId = brandMoodId;
        job.user = user;
        job.cutType = cutType;
        job.generationMode = generationMode;
        job.prompt = prompt;
        job.negativePrompt = negativePrompt;
        job.userOptionsJson = userOptionsJson;
        job.referenceJson = referenceJson;
        job.aiName = aiName;
        job.status = "PENDING";
        job.requestedCount = requestedCount;
        job.outputCount = 0;
        job.updatedAt = LocalDateTime.now();
        return job;
    }

    public void markProcessing() {
        this.status = "PROCESSING";
        this.updatedAt = LocalDateTime.now();
    }

    public void markSucceeded(int outputCount, int latencyMs) {
        this.status = "SUCCEEDED";
        this.outputCount = outputCount;
        this.latencyMs = latencyMs;
        this.errorMessage = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementOutputCount() {
        this.outputCount = this.outputCount + 1;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage, int latencyMs) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
        this.latencyMs = latencyMs;
        this.updatedAt = LocalDateTime.now();
    }
}
