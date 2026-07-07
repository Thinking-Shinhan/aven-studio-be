package kr.co.daeun.avenstudiobe.global.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@AllArgsConstructor
public class PageRespDto<T> {

    private List<T> content;

    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    private boolean first;
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