package kr.co.daeun.avenstudiobe.domain.imagegeneration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ShotTemplateListRespDto {

    private String cutType;
    private List<ShotTemplateRespDto> shotTemplates;
}
