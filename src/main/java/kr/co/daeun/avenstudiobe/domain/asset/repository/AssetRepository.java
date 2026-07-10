package kr.co.daeun.avenstudiobe.domain.asset.repository;

import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByProductIdOrderByIdDesc(Long productId);

    Optional<Asset> findByIdAndProduct_Brand_Id(Long id, Long brandId);

    boolean existsByProductIdAndAssetRoleAndTypeAndStatus(
            Long productId,
            String assetRole,
            String type,
            String status
    );
}
