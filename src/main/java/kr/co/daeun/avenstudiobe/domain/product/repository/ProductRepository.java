package kr.co.daeun.avenstudiobe.domain.product.repository;

import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("""
    SELECT p
    FROM Product p
    WHERE p.category IN :categories
      AND (:color IS NULL OR p.color = :color)
      AND (:gender IS NULL OR p.gender = :gender)
      AND (:keywordPattern IS NULL OR p.name LIKE :keywordPattern)
""")
    Page<Product> searchProductsWithCategory(
            @Param("categories") List<String> categories,
            @Param("color") String color,
            @Param("gender") String gender,
            @Param("keywordPattern") String keywordPattern,
            Pageable pageable
    );

    @Query("""
    SELECT p
    FROM Product p
    WHERE (:color IS NULL OR p.color = :color)
      AND (:gender IS NULL OR p.gender = :gender)
      AND (:keywordPattern IS NULL OR p.name LIKE :keywordPattern)
""")
    Page<Product> searchProductsWithoutCategory(
            @Param("color") String color,
            @Param("gender") String gender,
            @Param("keywordPattern") String keywordPattern,
            Pageable pageable
    );

    Product findProductByBrandAndCode(Brand brand, String code);

    Optional<Product> findByIdAndBrand_Id(Long id, Long brandId);
}
