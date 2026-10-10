package com.bahceden.shipping.dto;

import com.bahceden.shipping.domain.PackageSize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Gönderi açma isteği (shipments spec'i, add-shipments D3).
 *
 * @param packageSize koli boyutu; desi karşılığı entegratöre iletilir, ücreti etkilemez
 */
@Schema(name = "CreateShipmentRequest", description = "Kargoya hazırla: gönderi açma isteği")
public record CreateShipmentRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Koli boyutu: SMALL (küçük), MEDIUM (orta), LARGE (büyük)", example = "MEDIUM")
        @NotNull(message = "{validation.packageSize.required}")
        PackageSize packageSize) {
}
