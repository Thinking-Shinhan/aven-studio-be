package kr.co.daeun.avenstudiobe.domain.asset.repository;

import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByProductIdOrderByIdDesc(Long productId);

    List<Asset> findByProductIdOrderByIdAsc(Long productId);

    Optional<Asset> findByIdAndProduct_Brand_Id(Long id, Long brandId);

    @Query("""
            select a
            from Asset a
            left join fetch a.product p
            left join fetch p.brand
            where a.id = :id
            """)
    Optional<Asset> findByIdWithProductAndBrand(Long id);

    boolean existsByProductIdAndAssetRoleAndTypeAndStatus(
            Long productId,
            String assetRole,
            String type,
            String status
    );
}
