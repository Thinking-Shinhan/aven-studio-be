package kr.co.daeun.avenstudiobe.domain.user.repository;

import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsersRepository extends JpaRepository<User, Long> {
    @EntityGraph(attributePaths = "brand")
    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
