package kr.co.daeun.avenstudiobe.domain.imagegeneration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReferenceAssetRespDto {

    private Long referenceAssetId;
    private String key;
    private String assetType;
    private String label;
    private String description;
    private String imageUrl;
}
