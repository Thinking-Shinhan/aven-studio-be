package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ImagePlacementRespDto {

    private Integer blockOrder;
    private String instanceKey;
    private String templateKey;
    private String slotKey;
    private Long assetId;
    private String fileUrl;
    private String assetRole;
}
