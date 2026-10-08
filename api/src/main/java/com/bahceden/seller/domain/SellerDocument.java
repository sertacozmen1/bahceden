package com.bahceden.seller.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Başvuruyla gönderilen belgenin meta verisi (design.md D6).
 *
 * <p>Dosyanın kendisi veritabanında değil, yapılandırılmış dizindedir
 * (ADR-039); burada yalnızca sunucunun ürettiği ad ({@code storageKey}) ve
 * içerikten tespit edilen tür tutulur. Kullanıcının verdiği dosya adı hiçbir
 * yere yazılmaz.
 *
 * <p>Her başvuru ve yeniden gönderim yeni bir satırdır; kaydın güncel belgesi en
 * son satırdır. Önceki belgeler silinmez: onayın dayandığı belge denetim izinin
 * parçasıdır (ADR-037 §7). Ürün fotoğraflarıyla aynı tabloyu paylaşmaz: gizli
 * belge herkese açık bir uçtan sunulamaz.
 */
@Entity
@Table(name = "seller_documents")
public class SellerDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private SellerDocumentType documentType;

    @Column(name = "storage_key", nullable = false, length = 64, unique = true)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SellerDocument() {
    }

    public static SellerDocument of(long sellerId, SellerDocumentType documentType, String storageKey,
                                    String contentType, long sizeBytes, Instant now) {
        var document = new SellerDocument();
        document.sellerId = sellerId;
        document.documentType = documentType;
        document.storageKey = storageKey;
        document.contentType = contentType;
        document.sizeBytes = sizeBytes;
        document.createdAt = now;
        document.updatedAt = now;
        return document;
    }

    public Long getId() {
        return id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public SellerDocumentType getDocumentType() {
        return documentType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
