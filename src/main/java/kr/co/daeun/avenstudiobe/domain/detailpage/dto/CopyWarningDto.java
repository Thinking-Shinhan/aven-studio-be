package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CopyWarningDto {

    private String instanceKey;
    private String slotKey;
    private String code;
    private String message;
    private Integer recommendedMaxLength;
    private Integer actualLength;
}
