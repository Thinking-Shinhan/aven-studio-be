package kr.co.daeun.avenstudiobe.domain.detailpage.repository;

import kr.co.daeun.avenstudiobe.domain.detailpage.entity.DetailPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DetailPageRepository extends JpaRepository<DetailPage, Long> {
    Optional<DetailPage> findByProductId(Long productId);
}
