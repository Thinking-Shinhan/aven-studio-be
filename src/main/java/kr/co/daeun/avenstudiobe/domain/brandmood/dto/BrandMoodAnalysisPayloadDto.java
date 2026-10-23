package kr.co.daeun.avenstudiobe.domain.brandmood.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrandMoodAnalysisPayloadDto {

    private String sourceType;
    private BrandMoodAnalysisValuesDto internalAnalysis;
    private BrandMoodAnalysisValuesDto display;
}
