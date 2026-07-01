package kr.co.daeun.avenstudiobe.domain.brand.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "brands")
public class Brand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "site_url", length = 100)
    private String siteUrl;

    @Column(name = "mood_guide_url", length = 100)
    private String moodGuideUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Brand(String name, String siteUrl, String moodGuideUrl) {
        this.name = name;
        this.siteUrl = siteUrl;
        this.moodGuideUrl = moodGuideUrl;
    }
}
