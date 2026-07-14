package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import java.util.List;
import lombok.Getter;

@Getter
public class DetailPageAutoFillRespDto {

    private Long productId;
    private List<ImagePlacementRespDto> placements;
    private List<CopyResultDto> copies;
    private List<CopyWarningDto> warnings;
    private DetailPageAutoFillStatus status;
    private List<DetailPageAutoFillErrorDto> errors;

    public DetailPageAutoFillRespDto(
            Long productId,
            List<ImagePlacementRespDto> placements,
            List<CopyResultDto> copies,
            List<CopyWarningDto> warnings
    ) {
        this(
                productId,
                placements,
                copies,
                warnings,
                DetailPageAutoFillStatus.SUCCESS,
                List.of()
        );
    }

    public DetailPageAutoFillRespDto(
            Long productId,
            List<ImagePlacementRespDto> placements,
            List<CopyResultDto> copies,
            List<CopyWarningDto> warnings,
            DetailPageAutoFillStatus status,
            List<DetailPageAutoFillErrorDto> errors
    ) {
        this.productId = productId;
        this.placements = placements;
        this.copies = copies;
        this.warnings = warnings;
        this.status = status;
        this.errors = errors;
    }
}
