package kr.co.daeun.avenstudiobe.domain.brandmood.entity;

import jakarta.persistence.*;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "brand_moods")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandMood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "mood_type", nullable = false, length = 30)
    private String moodType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "source_type", length = 30)
    private String sourceType;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "brand_summary", columnDefinition = "TEXT")
    private String brandSummary;

    @Column(name = "design_philosophy", columnDefinition = "TEXT")
    private String designPhilosophy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tone_keywords", nullable = false, columnDefinition = "jsonb")
    private String toneKeywords;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mood_attributes", nullable = false, columnDefinition = "jsonb")
    private String moodAttributes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "color_palette", nullable = false, columnDefinition = "jsonb")
    private String colorPalette;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "negative_keywords", nullable = false, columnDefinition = "jsonb")
    private String negativeKeywords;

    @Column(name = "brand_instruction", nullable = false, columnDefinition = "TEXT")
    private String brandInstruction;

    @Column(name = "custom_brand_note", columnDefinition = "TEXT")
    private String customBrandNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_analysis_json", nullable = false, columnDefinition = "jsonb")
    private String rawAnalysisJson;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static BrandMood create(
            User user,
            Brand brand,
            String name,
            int version,
            String sourceType,
            String sourceUrl,
            String brandSummary,
            String designPhilosophy,
            String toneKeywords,
            String moodAttributes,
            String colorPalette,
            String negativeKeywords,
            String brandInstruction,
            String customBrandNote,
            String rawAnalysisJson
    ) {
        BrandMood brandMood = new BrandMood();
        brandMood.user = user;
        brandMood.brand = brand;
        brandMood.name = name;
        brandMood.version = version;
        brandMood.moodType = "BASE";
        brandMood.status = "ACTIVE";
        brandMood.sourceType = sourceType;
        brandMood.sourceUrl = sourceUrl;
        brandMood.brandSummary = brandSummary;
        brandMood.designPhilosophy = designPhilosophy;
        brandMood.toneKeywords = toneKeywords;
        brandMood.moodAttributes = moodAttributes;
        brandMood.colorPalette = colorPalette;
        brandMood.negativeKeywords = negativeKeywords;
        brandMood.brandInstruction = brandInstruction;
        brandMood.customBrandNote = customBrandNote;
        brandMood.rawAnalysisJson = rawAnalysisJson;
        brandMood.active = true;
        brandMood.updatedAt = LocalDateTime.now();
        return brandMood;
    }
}
