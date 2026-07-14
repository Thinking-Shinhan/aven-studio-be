package kr.co.daeun.avenstudiobe.domain.library.dto;

import kr.co.daeun.avenstudiobe.domain.detailpage.entity.DetailPage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DetailPageRespDto {
    private Long productId;
    private boolean hasDetailPage;
    private DetailPageInfo detailPage;

    @Getter
    @AllArgsConstructor
    public static class DetailPageInfo{
        private Long detailPageId;
        private String status;
        private String fileUrl;
        private String fileName;
        private LocalDateTime createdAt;

        public static DetailPageInfo from(DetailPage detailPage, String fileUrl){
            return new DetailPageInfo(
                    detailPage.getId(),
                    detailPage.getStatus(),
                    fileUrl,
                    StringUtils.getFilename(detailPage.getObjectName()),
                    detailPage.getCreatedAt()
            );
        }
    }

    public static DetailPageRespDto from(Long productId, DetailPage detailPage, String fileUrl){
        return new DetailPageRespDto(
                productId,
                true,
                DetailPageInfo.from(detailPage, fileUrl)
        );
    }

    public static DetailPageRespDto empty(Long productId) {
        return new DetailPageRespDto(
                productId,
                false,
                null
        );
    }

}
