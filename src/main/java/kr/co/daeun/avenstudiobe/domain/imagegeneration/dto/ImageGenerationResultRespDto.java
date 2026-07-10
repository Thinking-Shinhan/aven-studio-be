package kr.co.daeun.avenstudiobe.domain.imagegeneration.dto;

import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ImageGenerationResult;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImageGenerationResultRespDto {

    private Long resultId;
    private Long assetId;
    private int outputIndex;
    private String status;
    private String imageUrl;
    private String imageBase64;

    public static ImageGenerationResultRespDto from(ImageGenerationResult result, String imageUrl) {
        Long assetId = result.getAsset() == null ? null : result.getAsset().getId();

        return new ImageGenerationResultRespDto(
                result.getId(),
                assetId,
                result.getOutputIndex(),
                result.getStatus(),
                imageUrl,
                null
        );
    }

    public static ImageGenerationResultRespDto from(
            ImageGenerationResult result,
            String imageUrl,
            String imageBase64
    ) {
        Long assetId = result.getAsset() == null ? null : result.getAsset().getId();

        return new ImageGenerationResultRespDto(
                result.getId(),
                assetId,
                result.getOutputIndex(),
                result.getStatus(),
                imageUrl,
                imageBase64
        );
    }
}
