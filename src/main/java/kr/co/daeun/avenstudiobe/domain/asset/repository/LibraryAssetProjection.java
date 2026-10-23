package kr.co.daeun.avenstudiobe.domain.asset.repository;

import java.time.LocalDateTime;

public interface LibraryAssetProjection {

    Long getAssetId();

    String getType();

    String getObjectName();

    String getDescription();

    String getStatus();

    Boolean getIsLiked();

    LocalDateTime getCreatedAt();

    String getCutType();
}
