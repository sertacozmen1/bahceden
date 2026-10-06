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
@Schema(name = "PageResponse", description = "Sayfalı liste yanıtı")
public record PageResponse<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Sayfadaki kayıtlar") List<T> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Geçerli sayfa numarası (sıfırdan başlar)", example = "0") int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Uygulanan sayfa boyutu", example = "20") int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Süzgeçten geçen toplam kayıt sayısı", example = "137") long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Toplam sayfa sayısı", example = "7") int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
