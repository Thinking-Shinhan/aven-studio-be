package kr.co.daeun.avenstudiobe.domain.users.entity;

import jakarta.persistence.*;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(name = "name", length = 20)
    private String name;


    @Column(name = "password", nullable = false, length = 200)
    private String password; // 암호화된 값 저장

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Builder
    public Users(Long id, String loginId, String name, String password, LocalDateTime createdAt, Brand brand) {
        this.id = id;
        this.loginId = loginId;
        this.name = name;
        this.password = password;
        this.createdAt = createdAt;
        this.brand =  brand;
    }

}
