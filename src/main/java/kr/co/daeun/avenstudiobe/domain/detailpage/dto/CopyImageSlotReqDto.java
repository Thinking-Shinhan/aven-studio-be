package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CopyImageSlotReqDto {

    @NotBlank
    private String slotKey;

    @NotNull
    private Integer slotOrder;

    @NotNull
    private Long assetId;
}
