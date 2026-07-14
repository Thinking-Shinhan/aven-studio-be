package kr.co.daeun.avenstudiobe.domain.asset.repository;

public interface LibraryImageFolderProjection {

    Long getProductId();

    String getProductCode();

    String getProductName();

    String getCategory();

    Long getAssetCount();

    Long getThumbnailAssetId();

    String getThumbnailObjectName();
}
