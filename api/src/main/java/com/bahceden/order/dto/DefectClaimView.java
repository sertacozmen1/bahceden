package com.bahceden.order.dto;

import com.bahceden.catalog.domain.ProductUnit;
import com.bahceden.catalog.dto.MoneyView;
import com.bahceden.order.domain.DefectClaimStatus;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Ayıplı ürün talebi; alıcı ve satıcı aynı görünümü görür (ADR-080, ADR-084).
 * Fotoğraflar ayrı, korumalı uçtan konumlarıyla alınır.
 *
 * @param id               talebin kimliği (fotoğraf adresi bununla kurulur)
 * @param status           durum
 * @param description      alıcının açıklaması
 * @param lines            satırlar
 * @param photos           fotoğrafların konumu ve büyük boyutun ölçüleri
 * @param sellerDeadlineAt satıcının yanıt vermesi gereken son an (UTC)
 * @param sellerReason     satıcının ret gerekçesi; yoksa {@code null}
 * @param buyerDeadlineAt  teklif veya retten sonra alıcının yanıt son anı; yoksa {@code null}
 * @param refundAmount     iade edildiyse alıcıya dönen toplam; yoksa {@code null}
 * @param shippingRefunded kargo ücreti de iade edildi mi (yalnızca tam iadede)
 * @param resolvedAt       uç duruma geldiği an; yoksa {@code null}
 * @param createdAt        açıldığı an (UTC)
 */
@Schema(name = "DefectClaimView", description = "Ayıplı ürün talebi")
public record DefectClaimView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Talebin kimliği", example = "5")
        long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Durum", example = "OPEN")
        DefectClaimStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Alıcının açıklaması",
                example = "Domates kasasının üst sırası ezik geldi.")
        String description,
        @ArraySchema(schema = @Schema(implementation = Line.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satırlar"))
        List<Line> lines,
        @ArraySchema(schema = @Schema(implementation = Photo.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                        description = "Fotoğraflar, konum sırasıyla"))
        List<Photo> photos,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satıcının yanıt son anı (UTC)",
                example = "2026-10-15T10:00:00Z")
        Instant sellerDeadlineAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Satıcının ret gerekçesi", example = "Paket gönderilirken sağlamdı.")
        String sellerReason,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Teklif veya retten sonra alıcının yanıt son anı (UTC); geçerse teklif kabul, ret kesin sayılır",
                example = "2026-10-17T10:00:00Z")
        Instant buyerDeadlineAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "İade edildiyse alıcıya dönen toplam")
        MoneyView refundAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Kargo ücreti de iade edildi mi",
                example = "false")
        boolean shippingRefunded,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Uç duruma geldiği an (UTC)", example = "2026-10-14T09:00:00Z")
        Instant resolvedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Açıldığı an (UTC)",
                example = "2026-10-13T10:00:00Z")
        Instant createdAt) {

    public DefectClaimView {
        lines = List.copyOf(lines);
        photos = List.copyOf(photos);
    }

    /**
     * @param orderItemId     sipariş satırının kimliği
     * @param productName     ürün adı (sipariş anındaki)
     * @param unit            satış birimi
     * @param unitPrice       birim fiyat
     * @param claimedQuantity  alıcının bildirdiği adet
     * @param offeredQuantity  satıcının önerdiği adet; teklif yoksa {@code null}
     * @param refundedQuantity iade edilen adet; iade yoksa {@code null}
     */
    @Schema(name = "DefectClaimLineView", description = "Talepteki bir satır")
    public record Line(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sipariş satırının kimliği", example = "42")
            long orderItemId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ürün adı", example = "Domates")
            String productName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satış birimi", example = "KILOGRAM")
            ProductUnit unit,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Birim fiyat")
            MoneyView unitPrice,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Alıcının bildirdiği adet", example = "2")
            int claimedQuantity,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                    description = "Satıcının önerdiği adet", example = "1")
            Integer offeredQuantity,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                    description = "İade edilen adet", example = "1")
            Integer refundedQuantity) {
    }

    /**
     * @param position fotoğrafın konumu (0–2)
     * @param width    büyük boyutun genişliği
     * @param height   büyük boyutun yüksekliği
     */
    @Schema(name = "DefectClaimPhotoView", description = "Talep fotoğrafı")
    public record Photo(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Konum", example = "0")
            int position,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Büyük boyutun genişliği", example = "1600")
            int width,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Büyük boyutun yüksekliği", example = "1200")
            int height) {
    }
}
