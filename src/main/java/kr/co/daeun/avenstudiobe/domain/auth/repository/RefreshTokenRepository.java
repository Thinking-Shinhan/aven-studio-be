package kr.co.daeun.avenstudiobe.domain.auth.repository;

import kr.co.daeun.avenstudiobe.domain.users.entity.Users;
import kr.co.daeun.avenstudiobe.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUser(Users user);
    Optional<RefreshToken> findByUser(Users user);
}