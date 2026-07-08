package kr.co.daeun.avenstudiobe.domain.brandmood.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BrandMoodSaveReqDto {

    @Size(max = 100)
    private String name;

    @Size(max = 30)
    private String sourceType;

    @Size(max = 500)
    private String sourceUrl;

    private String brandSummary;

    private String designPhilosophy;

    private List<String> brandTone = new ArrayList<>();

    private String colorTemperature;

    private String saturation;

    private String contrast;

    private String lighting;

    private String surfaceTexture;

    private String backgroundMood;

    private String composition;

    private List<String> primaryColors = new ArrayList<>();

    private List<String> avoidElements = new ArrayList<>();

    private String customBrandNote;

    private String brandInstruction;

    @NotBlank
    private String rawAnalysisJson = "{}";
}
