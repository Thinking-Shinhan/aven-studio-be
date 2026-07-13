package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import java.util.List;

public record CopyGenerationResult(
        List<CopyResultDto> copies
) {
    public CopyGenerationResult {
        copies = copies == null ? List.of() : copies;
    }
}
