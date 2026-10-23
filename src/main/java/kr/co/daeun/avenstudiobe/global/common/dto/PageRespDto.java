package kr.co.daeun.avenstudiobe.global.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@AllArgsConstructor
@Schema(description = "페이지 응답")
public class PageRespDto<T> {

    @Schema(description = "페이지 콘텐츠")
    private List<T> content;

    @Schema(description = "현재 페이지 번호입니다. 0부터 시작합니다.", example = "0")
    private int page;
    @Schema(description = "페이지 크기", example = "20")
    private int size;
    @Schema(description = "전체 요소 수", example = "120")
    private long totalElements;
    @Schema(description = "전체 페이지 수", example = "6")
    private int totalPages;

    @Schema(description = "첫 페이지 여부", example = "true")
    private boolean first;
    @Schema(description = "마지막 페이지 여부", example = "false")
    private boolean last;

    public static <T> PageRespDto<T> from(Page<T> page) {
        return new PageRespDto<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
