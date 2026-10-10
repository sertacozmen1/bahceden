package com.bahceden.shipping.dto;

import com.bahceden.shipping.domain.PackageSize;
import com.bahceden.shipping.domain.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Bir satıcı siparişinin etkin gönderisi (shipments ve orders spec'leri,
 * add-shipments D2). Alıcı ve satıcı aynı görünümü alır; etiket ayrı ve
 * yalnızca satıcıya açık bir uçtan sunulur (D4).
 *
 * @param carrier        kargo firması
 * @param trackingNumber takip numarası; şubede gönderi kodu olarak da kullanılır
 * @param status         gönderinin durumu
 * @param packageSize    koli boyutu
 * @param createdAt      gönderinin açıldığı an (UTC)
 * @param handedOverAt   kargoya verildiği an; verilmediyse {@code null}
 * @param deliveredAt    teslim edildiği an; edilmediyse {@code null}
 */
@Schema(name = "Shipment", description = "Satıcı siparişinin kargo gönderisi")
public record ShipmentView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Kargo firması", example = "Ornek Kargo")
        String carrier,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Takip numarası; şubede gönderi kodu olarak da kullanılır", example = "FK0123456789")
        String trackingNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Gönderinin durumu", example = "IN_TRANSIT")
        ShipmentStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Koli boyutu", example = "MEDIUM")
        PackageSize packageSize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Gönderinin açıldığı an (UTC)",
                example = "2026-10-10T08:15:00Z")
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Kargoya verildiği an (UTC)", example = "2026-10-10T14:30:00Z")
        Instant handedOverAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Teslim edildiği an (UTC)", example = "2026-10-11T16:05:00Z")
        Instant deliveredAt) {
}
