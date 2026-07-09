package kr.co.daeun.avenstudiobe.domain.imagegeneration.repository;

import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ReferenceAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReferenceAssetRepository extends JpaRepository<ReferenceAsset, Long> {

    List<ReferenceAsset> findByActiveTrueOrderBySortOrderAscIdAsc();

    List<ReferenceAsset> findByReferenceTypeAndActiveTrueOrderBySortOrderAscIdAsc(String referenceType);
}
