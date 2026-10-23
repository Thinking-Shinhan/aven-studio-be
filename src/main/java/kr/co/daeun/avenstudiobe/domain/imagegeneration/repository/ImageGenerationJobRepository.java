package kr.co.daeun.avenstudiobe.domain.imagegeneration.repository;

import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ImageGenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;

@Repository
public interface ImageGenerationJobRepository extends JpaRepository<ImageGenerationJob, Long> {

    Optional<ImageGenerationJob> findByIdAndBrand_Id(Long id, Long brandId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ImageGenerationJob j where j.id = :jobId")
    Optional<ImageGenerationJob> findByIdForUpdate(@Param("jobId") Long jobId);
}
