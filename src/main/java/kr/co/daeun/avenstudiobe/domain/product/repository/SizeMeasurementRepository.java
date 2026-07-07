package kr.co.daeun.avenstudiobe.domain.product.repository;

import kr.co.daeun.avenstudiobe.domain.product.entity.Size;
import kr.co.daeun.avenstudiobe.domain.product.entity.SizeMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SizeMeasurementRepository extends JpaRepository<SizeMeasurement, Long> {

    @Modifying
    @Query("DELETE FROM SizeMeasurement sm WHERE sm.size IN :sizes")
    void deleteBySizeIn(@Param("sizes") List<Size> sizes);
}
