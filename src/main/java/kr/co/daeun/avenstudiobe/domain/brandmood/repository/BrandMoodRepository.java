package kr.co.daeun.avenstudiobe.domain.brandmood.repository;

import kr.co.daeun.avenstudiobe.domain.brandmood.entity.BrandMood;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrandMoodRepository extends JpaRepository<BrandMood, Long> {

    Optional<BrandMood> findTopByBrandIdOrderByVersionDesc(Long brandId);
}
