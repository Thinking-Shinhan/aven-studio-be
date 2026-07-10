package kr.co.daeun.avenstudiobe.domain.imagegeneration.repository;

import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ImageGenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImageGenerationJobRepository extends JpaRepository<ImageGenerationJob, Long> {

    Optional<ImageGenerationJob> findByIdAndBrand_Id(Long id, Long brandId);
}
