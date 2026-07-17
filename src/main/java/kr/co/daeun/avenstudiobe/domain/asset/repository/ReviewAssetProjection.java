package kr.co.daeun.avenstudiobe.domain.asset.repository;

public interface ReviewAssetProjection {

    Long getId();

    String getObjectName();

    String getContentType();

    String getAssetRole();

    String getDescription();
}
