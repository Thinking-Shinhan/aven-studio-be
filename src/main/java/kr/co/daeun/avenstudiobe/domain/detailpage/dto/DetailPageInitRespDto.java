package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DetailPageInitRespDto {
    private ProductInitRespDto product;
    private List<AssetInitRespDto> assets;
}
