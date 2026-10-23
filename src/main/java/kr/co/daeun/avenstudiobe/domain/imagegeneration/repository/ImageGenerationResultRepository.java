package kr.co.daeun.avenstudiobe.domain.imagegeneration.repository;

import kr.co.daeun.avenstudiobe.domain.imagegeneration.entity.ImageGenerationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImageGenerationResultRepository extends JpaRepository<ImageGenerationResult, Long> {

    List<ImageGenerationResult> findByJobIdOrderByOutputIndexAsc(Long jobId);
}
