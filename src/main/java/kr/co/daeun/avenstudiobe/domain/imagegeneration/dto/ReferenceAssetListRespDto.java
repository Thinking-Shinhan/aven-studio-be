package kr.co.daeun.avenstudiobe.domain.imagegeneration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ReferenceAssetListRespDto {

    private String assetType;
    private List<ReferenceAssetRespDto> referenceAssets;
}
