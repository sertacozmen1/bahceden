package com.bahceden.common.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Tüm liste uçlarının ortak yanıt şekli (AGENTS.md §11.5).
 *
 * <p>Sayfalı yanıt her uçta <strong>aynı</strong> alanları taşır: istemci tek bir
 * sayfalama bileşeni yazar ve her uç için yeniden öğrenmez. Spring'in kendi
 * {@code Page} serileştirmesi bilinçli olarak dışarıya verilmez: iç yapısı
 * sürümler arasında değişebilir ve sözleşmeye kütüphane detayı sızdırır.
 *
 * @param items         sayfadaki kayıtlar
 * @param page          geçerli sayfa numarası (sıfırdan başlar)
 * @param size          <strong>uygulanan</strong> sayfa boyutu; istek üst sınırı
 *                      aşarsa sunucu değeri sınıra çeker ve gerçek değeri burada bildirir
 * @param totalElements süzgeçten geçen toplam kayıt sayısı
 * @param totalPages    toplam sayfa sayısı
 */
// Sabit şema adı verilmez: generic kayıt her öğe tipi için ayrı şema olmalı
// (PageResponseProductSummary, PageResponseAdminSellerSummary). Tek ad, iki liste
// ucunu aynı şemaya bağlar ve birinin öğe tipini yanlış gösterirdi.
@Schema(description = "Sayfalı liste yanıtı")
public record PageResponse<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sayfadaki kayıtlar") List<T> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Geçerli sayfa numarası (sıfırdan başlar)", example = "0") int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Uygulanan sayfa boyutu", example = "20") int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Süzgeçten geçen toplam kayıt sayısı", example = "137") long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Toplam sayfa sayısı", example = "7") int totalPages) {

    /**
     * Sunucuda üst sınırı sabit, kısa bir listenin tamamı tek sayfa olarak
     * (teslimat illeri, en çok 10 adres; add-cart-and-checkout D5). Liste
     * yanıtı her uçta aynı şekli korur (AGENTS.md §11.5).
     */
    public static <T> PageResponse<T> ofAll(List<T> items) {
        return new PageResponse<>(List.copyOf(items), 0, items.size(), items.size(), 1);
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
