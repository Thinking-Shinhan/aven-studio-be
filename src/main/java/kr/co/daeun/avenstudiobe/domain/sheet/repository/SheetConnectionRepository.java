package kr.co.daeun.avenstudiobe.domain.sheet.repository;

import kr.co.daeun.avenstudiobe.domain.sheet.entity.SheetConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface SheetConnectionRepository extends JpaRepository<SheetConnection, Long> {

    Optional<SheetConnection> findByBrandId(Long brandId);

    @Transactional
    @Modifying
    @Query("""
    UPDATE SheetConnection s
    SET s.syncStatus = kr.co.daeun.avenstudiobe.domain.sheet.entity.SyncStatus.IN_PROGRESS
    WHERE s.id = :id AND s.syncStatus <> kr.co.daeun.avenstudiobe.domain.sheet.entity.SyncStatus.IN_PROGRESS
""")
    int tryMarkInProgress(@Param("id") Long id);
}
