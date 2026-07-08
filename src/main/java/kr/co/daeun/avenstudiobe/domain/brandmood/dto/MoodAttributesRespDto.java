package kr.co.daeun.avenstudiobe.domain.brandmood.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoodAttributesRespDto {

    private String colorTemperature;
    private String saturation;
    private String contrast;
    private String lighting;
    private String surfaceTexture;
    private String backgroundMood;
    private String composition;
}
