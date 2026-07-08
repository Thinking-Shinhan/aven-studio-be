package kr.co.daeun.avenstudiobe.domain.brandmood.dto;

import kr.co.daeun.avenstudiobe.domain.brandmood.entity.BrandMood;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class BrandMoodRespDto {

    private Long brandMoodId;
    private Long brandId;
    private String name;
    private Integer version;
    private String status;
    private String sourceType;
    private String sourceUrl;
    private String brandSummary;
    private String designPhilosophy;
    private List<String> brandTone;
    private String colorTemperature;
    private String saturation;
    private String contrast;
    private String lighting;
    private String surfaceTexture;
    private String backgroundMood;
    private String composition;
    private List<String> primaryColors;
    private List<String> avoidElements;
    private String customBrandNote;
    private String brandInstruction;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BrandMoodRespDto from(
            BrandMood brandMood,
            List<String> brandTone,
            MoodAttributesRespDto moodAttributes,
            List<String> primaryColors,
            List<String> avoidElements
    ) {
        return new BrandMoodRespDto(
                brandMood.getId(),
                brandMood.getBrand().getId(),
                brandMood.getName(),
                brandMood.getVersion(),
                brandMood.getStatus(),
                brandMood.getSourceType(),
                brandMood.getSourceUrl(),
                brandMood.getBrandSummary(),
                brandMood.getDesignPhilosophy(),
                brandTone,
                moodAttributes.getColorTemperature(),
                moodAttributes.getSaturation(),
                moodAttributes.getContrast(),
                moodAttributes.getLighting(),
                moodAttributes.getSurfaceTexture(),
                moodAttributes.getBackgroundMood(),
                moodAttributes.getComposition(),
                primaryColors,
                avoidElements,
                brandMood.getCustomBrandNote(),
                brandMood.getBrandInstruction(),
                brandMood.getCreatedAt(),
                brandMood.getUpdatedAt()
        );
    }
}
