package kr.co.daeun.avenstudiobe.domain.brandmood.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrandMoodAnalysisValuesDto {

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
}
