package kr.co.daeun.avenstudiobe.domain.imagegeneration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ImageGenerationOptionsRespDto {

    private List<OptionRespDto> cutTypes;
    private List<OptionRespDto> colorTemperatures;
    private List<OptionRespDto> cameraAngles;
    private List<OptionRespDto> framings;
    private List<OptionRespDto> aspectRatios;
    private List<OptionRespDto> outputCounts;
}
