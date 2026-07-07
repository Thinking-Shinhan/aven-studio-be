package kr.co.daeun.avenstudiobe.domain.sheet.entity;

import jakarta.persistence.*;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sheet_connections", uniqueConstraints = @UniqueConstraint(columnNames = "brand_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class SheetConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "url", nullable = false, length = 300)
    private String url;

    @Column(name = "spreadsheet_id", length= 200)
    private String spreadsheetId;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", length = 20)
    private SyncStatus syncStatus;

    @Column(name = "sync_message", columnDefinition = "text")
    private String syncMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    public static SheetConnection create(Brand brand, String url, String spreadsheetId) {
        SheetConnection sheetConnection = new SheetConnection();
        sheetConnection.brand = brand;
        sheetConnection.markConnected(url, spreadsheetId);
        return sheetConnection;
    }

    public void markConnected(String url, String spreadsheetId) {
        this.url = url;
        this.spreadsheetId = spreadsheetId;
        this.syncStatus = SyncStatus.PENDING;
    }

    public void markInProgress() {
        this.syncStatus = SyncStatus.IN_PROGRESS;
    }

    public void markSuccess(String message) {
        this.syncStatus = SyncStatus.SUCCESS;
        this.lastSyncedAt = LocalDateTime.now();
        this.syncMessage = message;
    }

    public void markPartialSuccess(String message) {
        this.syncStatus = SyncStatus.PARTIAL_SUCCESS;
        this.lastSyncedAt = LocalDateTime.now();
        this.syncMessage = message;
    }

    public void markFailed(String message) {
        this.syncStatus = SyncStatus.FAILED;
        this.syncMessage = message;
    }
}
