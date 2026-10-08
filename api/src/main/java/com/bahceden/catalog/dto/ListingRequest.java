package com.bahceden.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * İlan oluşturma veya güncelleme isteği (product-listings spec'i).
 *
 * <p>Fiyat minor unit tamsayıdır (ADR-006); para birimi her zaman TRY'dir ve
 * istemci göndermez. Birim metin olarak alınıp kalıpla doğrulanır: tanınmayan
 * değer okunamayan istek değil, yerelleştirilmiş alan hatası olur
 * (ADR-042'deki gerekçe).
 *
 * @param name          ilan adı
 * @param description   isteğe bağlı açıklama; düz metin
 * @param unit          satış birimi: {@code KILOGRAM}, {@code PIECE} veya {@code BUNCH}
 * @param priceAmount   birim fiyat, kuruş
 * @param stockQuantity eldeki miktar
 */
@Schema(name = "ListingRequest", description = "İlan oluşturma veya güncelleme isteği")
public record ListingRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "İlan adı", example = "Salkım Domates")
        @NotBlank(message = "{validation.listingName.required}")
        @Size(min = 2, max = 200, message = "{validation.listingName.length}")
        String name,

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true,
                description = "Açıklama; düz metin", example = "Tarla domatesi, sabah toplandı.")
        @Size(max = 2000, message = "{validation.listingDescription.max}")
        String description,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Satış birimi",
                allowableValues = {"KILOGRAM", "PIECE", "BUNCH"}, example = "KILOGRAM")
        @NotBlank(message = "{validation.listingUnit.required}")
        @Pattern(regexp = "KILOGRAM|PIECE|BUNCH", message = "{validation.listingUnit.allowed}")
        String unit,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Birim fiyat, kuruş (2450 = 24,50 TL); para birimi her zaman TRY", example = "2450")
        @NotNull(message = "{validation.listingPrice.required}")
        @Min(value = 1, message = "{validation.listingPrice.range}")
        @Max(value = 10_000_000, message = "{validation.listingPrice.range}")
        Long priceAmount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Eldeki miktar; sıfır stokta yok demek",
                example = "80")
        @NotNull(message = "{validation.listingStock.required}")
        @Min(value = 0, message = "{validation.listingStock.range}")
        @Max(value = 100_000, message = "{validation.listingStock.range}")
        Integer stockQuantity) {
}
