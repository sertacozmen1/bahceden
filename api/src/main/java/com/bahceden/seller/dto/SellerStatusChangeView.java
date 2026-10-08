package com.bahceden.seller.dto;

import com.bahceden.seller.domain.SellerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Satıcı kaydının geçmişindeki bir durum geçişi (ADR-037 §7).
 *
 * @param fromStatus önceki durum; başvuruda boş
 * @param toStatus   yeni durum
 * @param reason     gerekçe; ret ve askıda dolu
 * @param actorEmail geçişi yapan hesabın e-postası (başvuran veya admin)
 * @param createdAt  geçişin anı (UTC)
 */
@Schema(name = "SellerStatusChangeView", description = "Satıcı kaydının bir durum geçişi")
public record SellerStatusChangeView(

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Önceki durum; başvuruda boş", example = "PENDING")
        SellerStatus fromStatus,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Yeni durum", example = "REJECTED")
        SellerStatus toStatus,

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Gerekçe; ret ve askıda dolu", example = "Belgedeki ad okunmuyor.")
        String reason,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Geçişi yapan hesabın e-postası", example = "admin@ornek.test")
        String actorEmail,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Geçişin anı (UTC, ISO-8601)", example = "2026-10-08T10:15:00Z")
        Instant createdAt) {
}
