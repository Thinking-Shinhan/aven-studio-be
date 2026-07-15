package kr.co.daeun.avenstudiobe.domain.library.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LikedAssetRespDto {
    private Long productId;
    private boolean isLiked;
}
