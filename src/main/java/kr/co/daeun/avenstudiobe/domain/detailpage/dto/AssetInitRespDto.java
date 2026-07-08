package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.checkerframework.checker.units.qual.A;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AssetInitRespDto {
    private Long id;
    private String fileUrl;
    private LocalDateTime createdAt;
}
