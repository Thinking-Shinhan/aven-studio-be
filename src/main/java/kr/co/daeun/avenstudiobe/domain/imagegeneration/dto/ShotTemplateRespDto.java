package kr.co.daeun.avenstudiobe.domain.imagegeneration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ShotTemplateRespDto {

    private Long shotTemplateId;
    private String key;
    private String label;
    private String cutType;
    private String promptText;
    private String description;
    private String imageUrl;
}
