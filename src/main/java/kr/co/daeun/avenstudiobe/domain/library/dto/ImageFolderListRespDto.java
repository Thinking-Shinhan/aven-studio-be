package kr.co.daeun.avenstudiobe.domain.library.dto;

import java.util.List;

public record ImageFolderListRespDto(
        List<ImageFolderRespDto> imageFolders
) {
}
