package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

public record DetailPageSaveRespDto(
        Long detailPageId,
        Long productId,
        String status,
        String fileUrl,
        String fileName
) {
}
