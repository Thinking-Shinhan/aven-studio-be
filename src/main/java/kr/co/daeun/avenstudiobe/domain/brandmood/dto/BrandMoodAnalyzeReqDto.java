package kr.co.daeun.avenstudiobe.domain.brandmood.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BrandMoodAnalyzeReqDto {

    @Size(max = 30)
    private String sourceType;

    @Size(max = 500)
    private String sourceUrl;

    private String sourceText;

    private String customInstruction;
}
