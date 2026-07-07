package kr.co.daeun.avenstudiobe.domain.product.repository;

import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.entity.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SizeRepository extends JpaRepository<Size, Long> {

    List<Size> findByProduct(Product product);

    @Modifying
    @Query("DELETE FROM Size s WHERE s.product = :product")
    void deleteByProduct(@Param("product") Product product);
}
