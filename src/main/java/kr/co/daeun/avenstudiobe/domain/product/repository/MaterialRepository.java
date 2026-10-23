package kr.co.daeun.avenstudiobe.domain.product.repository;

import kr.co.daeun.avenstudiobe.domain.product.entity.Material;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Long> {

    @Modifying
    @Query("DELETE FROM Material m WHERE m.product = :product")
    void deleteByProduct(@Param("product") Product product);

    List<Material> findByProductId(Long productId);
}
