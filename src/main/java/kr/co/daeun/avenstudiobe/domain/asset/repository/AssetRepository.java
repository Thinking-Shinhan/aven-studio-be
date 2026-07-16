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

    @Query("""
            select a
            from Asset a
            join fetch a.product p
            where p.id in :productIds
              and a.assetRole in :assetRoles
              and a.type = 'IMAGE'
              and a.status in ('USED', 'ACTIVE')
            order by a.id desc
            """)
    List<Asset> findProductImagesForProductList(
            @Param("productIds") List<Long> productIds,
            @Param("assetRoles") Set<String> assetRoles
    );

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
        with image_counts as (
            select a.product_id,
                   count(*) filter (
                       where j.cut_type = 'MODEL_CUT'
                   ) as model_cut_count,
                   count(*) filter (
                       where j.cut_type = 'PRODUCT_CUT'
                   ) as product_cut_count,
                   max(a.created_at) as image_updated_at
            from assets a
            left join image_generation_results r
                   on r.asset_id = a.id
            left join image_generation_jobs j
                   on j.id = r.job_id
            where a.type = 'GENERATED'
              and a.status = 'GENERATED'
            group by a.product_id
        ),
        detail_page_counts as (
            select dp.product_id,
                   count(dp.id) as detail_page_count,
                   max(dp.created_at) as detail_page_created_at
            from detail_pages dp
            group by dp.product_id
        )
        select p.id as "productId",
               p.code as "productCode",
               p.name as "productName",
               p.category as "category",
               coalesce(ic.model_cut_count, 0) as "modelCutCount",
               coalesce(ic.product_cut_count, 0) as "productCutCount",
               coalesce(dc.detail_page_count, 0) as "detailPageCount",
               greatest(
                   ic.image_updated_at,
                   dc.detail_page_updated_at
               ) as "updatedAt"
        from products p
        left join image_counts ic
               on ic.product_id = p.id
        left join detail_page_counts dc
               on dc.product_id = p.id
        where p.brand_id = :brandId
          and (
              ic.product_id is not null
              or dc.product_id is not null
          )
          and (
              cast(:category as varchar) is null
              or p.category = cast(:category as varchar)
          )
        order by greatest(
                     ic.image_updated_at,
                     dc.detail_page_updated_at
                 ) desc nulls last,
                 p.id desc
        """, nativeQuery = true)
        List<LibraryImageFolderProjection> findLibraryImageFolders(
        @Param("brandId") Long brandId,
        @Param("category") String category
        );
        @Query(value = """
        select ranked.product_id as "productId",
               ranked.asset_id as "assetId",
               ranked.object_name as "objectName"
        from (
            select a.product_id,
                   a.id as asset_id,
                   a.object_name,
                   row_number() over (
                       partition by a.product_id
                       order by a.created_at desc, a.id desc
                   ) as row_number
            from assets a
            where a.product_id in (:productIds)
              and a.type = 'GENERATED'
              and a.status = 'GENERATED'
        ) ranked
        where ranked.row_number <= 3
        order by ranked.product_id, ranked.row_number
        """, nativeQuery = true)
        List<LibraryPreviewImageProjection> findLibraryPreviewImages(
        @Param("productIds") List<Long> productIds
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
