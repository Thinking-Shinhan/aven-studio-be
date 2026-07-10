package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImagePlacementRespDto {

    private String instanceKey;
    private String slotKey;
    private Long assetId;
    private String fileUrl;
    private String assetRole;
}
