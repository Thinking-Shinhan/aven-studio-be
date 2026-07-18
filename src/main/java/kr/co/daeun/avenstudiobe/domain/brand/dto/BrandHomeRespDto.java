package kr.co.daeun.avenstudiobe.domain.brand.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class BrandHomeRespDto {
    private String brandName;
    private boolean brandMoodRegistered;
    private String brandSummary;
    private String designPhilosophy;
    private List<String> toneKeywords;
    private long productCount;
    private boolean sheetSynced;
    private LocalDateTime sheetSyncedAt;
}
