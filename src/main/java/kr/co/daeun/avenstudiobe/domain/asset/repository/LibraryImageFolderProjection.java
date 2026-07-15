package kr.co.daeun.avenstudiobe.domain.asset.repository;

import java.time.LocalDateTime;

public interface LibraryImageFolderProjection {

    Long getProductId();

    String getProductCode();

    String getProductName();

    String getCategory();

    Long getModelCutCount();

    Long getProductCutCount();

    Long getDetailPageCount();

    LocalDateTime getUpdatedAt();
}