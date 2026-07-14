package kr.co.daeun.avenstudiobe.domain.asset.repository;

import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @Query("""
            select a.id as id,
                   a.assetRole as role,
                   a.description as description
            from Asset a
            where a.product.id = :productId
              and a.id in :assetIds
            """)
    List<AssetCopyProjection> findCopyContexts(Long productId, Set<Long> assetIds);

    @Query(value = """
            select p.id as productId,
                   p.code as productCode,
                   p.name as productName,
                   p.category as category,
                   count(a.id) as assetCount,
                   max(a.id) as thumbnailAssetId,
                   (
                       select a2.object_name
                       from assets a2
                       where a2.product_id = p.id
                         and a2.type = 'GENERATED'
                         and a2.status = 'GENERATED'
                       order by a2.id desc
                       limit 1
                   ) as thumbnailObjectName
            from products p
            join assets a on a.product_id = p.id
            where p.brand_id = :brandId
              and a.type = 'GENERATED'
              and a.status = 'GENERATED'
              and (cast(:category as varchar) is null or p.category = cast(:category as varchar))
            group by p.id, p.code, p.name, p.category
            order by max(a.created_at) desc, p.id desc
            """, nativeQuery = true)
    List<LibraryImageFolderProjection> findLibraryImageFolders(
            @Param("brandId") Long brandId,
            @Param("category") String category
    );

    List<Asset> findByProduct_IdAndProduct_Brand_IdAndTypeAndStatusOrderByCreatedAtDescIdDesc(
            Long productId,
            Long brandId,
            String type,
            String status
    );

    Optional<Asset> findByIdAndProduct_Brand_IdAndTypeAndStatus(
            Long id,
            Long brandId,
            String type,
            String status
    );
}
