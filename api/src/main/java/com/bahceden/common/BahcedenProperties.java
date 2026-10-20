package com.bahceden.common;

import com.bahceden.common.geo.TurkishProvinces;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Uygulamaya özgü ayarlar.
 *
 * <p>Ayarlar yalnızca tip güvenli bağlama ile okunur; dağınık {@code @Value} ve
 * kodda sabit host/secret yasaktır (AGENTS.md §11.3, K10). {@code @Validated}
 * sayesinde eksik veya boş bir değerle uygulama <strong>başlamaz</strong>: yanlış
 * yapılandırma çalışma zamanında değil ayağa kalkarken görünür.
 *
 * @param webOrigin CORS için izin verilen tek kaynak ve kullanıcıya gönderilen
 *                  bağlantıların tabanı (doğrulama bağlantısı buradan kurulur).
 *                  Joker karakter kullanılmaz (AGENTS.md §11.6).
 * @param mailFrom  giden e-postaların gönderen adresi
 * @param jwt       erişim token'ı imza ayarları (ADR-008)
 * @param storage   yüklenen dosyaların dizini ve sınırları (ADR-039)
 * @param payment   ödeme sağlayıcısı seçimi (ADR-054)
 * @param commerce  komisyon, kargo ücreti, ödeme süresi ve teslimat illeri (ADR-055, ADR-058)
 * @param shipping  kargo entegratörü ve gönderi durumu sorgusu (ADR-071, ADR-075)
 * @param claims    ayıplı ürün talebinin süreleri (ADR-081, ADR-083)
 * @param cleanup   ödenmemiş sipariş ve süresi geçmiş token temizliği (add-stale-record-cleanup)
 */
@Validated
@ConfigurationProperties(prefix = "bahceden")
public record BahcedenProperties(
        @NotBlank String webOrigin,
        @NotBlank @Email String mailFrom,
        @NotNull @Valid Jwt jwt,
        @NotNull @Valid Storage storage,
        @NotNull @Valid Payment payment,
        @NotNull @Valid Commerce commerce,
        @NotNull @Valid Shipping shipping,
        @NotNull @Valid Claims claims,
        @NotNull @Valid Cleanup cleanup) {

    /**
     * Temizlik işleri (add-stale-record-cleanup D1, D2).
     *
     * <p>Sıfır veya negatif süreyle uygulama açılmaz.
     *
     * @param enabled                 zamanlayıcı açık mı; testlerde kapalı, testler işleri doğrudan çağırır
     * @param interval                iki tur arasındaki bekleme
     * @param pendingPaymentGrace     ödeme süresi dolduktan sonra işin siparişe dokunmadan beklediği süre;
     *                                alıcının ödeme dönüşü önceliklidir
     * @param pendingPaymentBatchSize bir turda işlenen en çok ödenmemiş sipariş
     * @param tokenRetention          süresi dolmuş token satırının silinmeden önce kaldığı süre
     */
    public record Cleanup(boolean enabled, @NotNull Duration interval, @NotNull Duration pendingPaymentGrace,
                          @Positive int pendingPaymentBatchSize, @NotNull Duration tokenRetention) {

        public Cleanup {
            Claims.requirePositive(interval, "bahceden.cleanup.interval");
            Claims.requirePositive(pendingPaymentGrace, "bahceden.cleanup.pending-payment-grace");
            Claims.requirePositive(tokenRetention, "bahceden.cleanup.token-retention");
        }
    }

    /**
     * Ayıplı ürün talebinin süreleri (ADR-056 §4, ADR-081, ADR-083).
     *
     * <p>Süreler takvim saatidir; hafta sonu dışlanmaz. Sıfır veya negatif
     * süreyle uygulama açılmaz.
     *
     * @param window         teslimden itibaren talep açılabilen süre
     * @param sellerResponse satıcının talebe yanıt süresi; geçerse talep yönetime düşer
     * @param buyerResponse  alıcının teklife veya redde yanıt süresi; geçerse teklif kabul, ret kesinleşmiş sayılır
     * @param sweepEnabled   süre işi zamanlayıcısı açık mı; testlerde kapalı, testler işi doğrudan çağırır
     * @param sweepInterval  iki süre turu arasındaki bekleme
     * @param sweepBatchSize bir turda işlenen en çok talep
     */
    public record Claims(@NotNull Duration window, @NotNull Duration sellerResponse,
                         @NotNull Duration buyerResponse, boolean sweepEnabled, @NotNull Duration sweepInterval,
                         @Positive int sweepBatchSize) {

        public Claims {
            requirePositive(window, "bahceden.claims.window");
            requirePositive(sellerResponse, "bahceden.claims.seller-response");
            requirePositive(buyerResponse, "bahceden.claims.buyer-response");
            requirePositive(sweepInterval, "bahceden.claims.sweep-interval");
        }

        private static void requirePositive(Duration value, String name) {
            if (value != null && (value.isZero() || value.isNegative())) {
                throw new IllegalArgumentException(name + " pozitif bir süre olmalıdır");
            }
        }
    }

    /**
     * Kargo entegratörü ve gönderi durumu sorgusu (ADR-057, add-shipments D1, D5).
     *
     * <p>Gerçek entegratör seçilene kadar tek geçerli değer {@code fake}'tir
     * (KALANLAR A2): bilinmeyen bir değerle uygulama açılmaz.
     *
     * @param integrator     entegratör adı
     * @param pollingEnabled durum sorgusu zamanlayıcısı açık mı; testlerde kapalı,
     *                       testler sorguyu doğrudan çağırır
     * @param pollInterval   iki sorgu turu arasındaki bekleme
     * @param pollBatchSize  bir turda sorgulanan en çok gönderi
     * @param packageDesi    koli boyutunun entegratöre bildirilen desi karşılığı (D3)
     * @param fake           sahte entegratörün durum ilerletme süreleri
     */
    public record Shipping(
            @NotBlank @Pattern(regexp = "fake") String integrator,
            boolean pollingEnabled,
            @NotNull Duration pollInterval,
            @Positive int pollBatchSize,
            @NotNull @Valid PackageDesi packageDesi,
            @NotNull @Valid FakeIntegrator fake) {

        /** Koli boyutlarının desi karşılığı; ücreti etkilemez (ADR-058). */
        public record PackageDesi(@Positive int small, @Positive int medium, @Positive int large) {
        }

        /**
         * Sahte entegratör gönderiyi açılışından bu süreler geçince ilerletir (D5).
         *
         * @param handedOverAfter kargoya verildi
         * @param inTransitAfter  yolda
         * @param deliveredAfter  teslim edildi
         */
        public record FakeIntegrator(@NotNull Duration handedOverAfter, @NotNull Duration inTransitAfter,
                                     @NotNull Duration deliveredAfter) {

            public FakeIntegrator {
                if (handedOverAfter != null && inTransitAfter != null && deliveredAfter != null
                        && (handedOverAfter.compareTo(inTransitAfter) > 0 || inTransitAfter.compareTo(deliveredAfter) > 0)) {
                    throw new IllegalArgumentException(
                            "bahceden.shipping.fake süreleri sıralı olmalıdır: handed-over ≤ in-transit ≤ delivered");
                }
            }
        }
    }

    /**
     * Ödeme sağlayıcısı (ADR-054, add-cart-and-checkout D8).
     *
     * <p>Gerçek sağlayıcı seçilene kadar tek geçerli değer {@code fake}'tir
     * (KALANLAR A1): bilinmeyen bir değerle uygulama açılmaz; yanlış yazılmış
     * bir ad sessizce sahte sağlayıcıya düşmez.
     *
     * @param provider sağlayıcı adı
     */
    public record Payment(@NotBlank @Pattern(regexp = "fake") String provider) {
    }

    /**
     * Sipariş fiyatlaması ve teslimat kuralları (add-cart-and-checkout D5, D7).
     *
     * <p>Değerler sipariş anında satıcı siparişine yazılır; burada değişmesi
     * geçmiş siparişi etkilemez (ADR-055 §5).
     *
     * @param commissionRateBp           komisyon oranı, baz puan (%5 = 500)
     * @param commissionFixedMinor       satıcı siparişi başına sabit komisyon, kuruş
     * @param shippingFeeMinor           satıcı başına kargo ücreti, kuruş (ADR-058)
     * @param freeShippingThresholdMinor bu ürün tutarı ve üzerinde kargo ücretsiz, kuruş
     * @param paymentTimeout             ödemenin tamamlanması için süre (D8)
     * @param deliveryProvinces          teslimat yapılan illerin plaka kodları; her biri il kataloğunda olmalı
     */
    public record Commerce(
            @Min(0) @Max(10_000) int commissionRateBp,
            @PositiveOrZero long commissionFixedMinor,
            @PositiveOrZero long shippingFeeMinor,
            @Positive long freeShippingThresholdMinor,
            @NotNull Duration paymentTimeout,
            @NotEmpty List<String> deliveryProvinces) {

        public Commerce {
            if (deliveryProvinces != null) {
                for (var code : deliveryProvinces) {
                    if (!TurkishProvinces.exists(code)) {
                        throw new IllegalArgumentException(
                                "bahceden.commerce.delivery-provinces içinde bilinmeyen il kodu: " + code);
                    }
                }
                deliveryProvinces = List.copyOf(deliveryProvinces);
            }
        }
    }

    /**
     * Yüklenen dosyaların saklandığı dizin (ADR-039, add-seller-onboarding D6).
     *
     * <p>Dizin uygulama kökünün ve sınıf yolunun dışındadır; geliştirmede bir
     * Docker volume'udur. Varlığı ve yazılabilirliği açılışta doğrulanır
     * ({@code FileStorage}): yanlış yapılandırma ilk yüklemede değil açılışta
     * görünür.
     *
     * @param dir              kök dizin; yalnızca ortamdan gelir
     * @param maxDocumentBytes başvuru belgesinin üst sınırı (bayt). Multipart
     *                         sınırı ({@code spring.servlet.multipart}) bununla
     *                         uyumlu tutulur, böylece büyük istek belleğe
     *                         alınmadan reddedilir.
     * @param maxPhotoBytes    ilan fotoğrafının üst sınırı (bayt; add-product-listings D4)
     */
    public record Storage(@NotBlank String dir, @Positive long maxDocumentBytes, @Positive long maxPhotoBytes) {
    }

    /**
     * Erişim token'ı imza ayarları.
     *
     * <p>Anahtar HMAC-SHA256 içindir ve en az 256 bit olmalıdır; daha kısa bir
     * anahtar kaba kuvvete açıktır. Kısa, Base64 olmayan veya yer tutucu bir
     * değerle uygulama <strong>açılmaz</strong> (K10 fail-fast). Hata mesajı
     * değerin kendisini içermez.
     *
     * @param secret Base64 kodlu imza anahtarı; yalnızca ortamdan gelir
     */
    public record Jwt(@NotBlank String secret) {

        /** HS256 için asgari anahtar uzunluğu: 32 bayt = 256 bit. */
        private static final int MIN_KEY_BYTES = 32;

        public Jwt {
            if (secret != null && !secret.isBlank()) {
                decode(secret);
            }
        }

        /** Çözülmüş anahtar baytları; her çağrıda yeni dizi döner. */
        public byte[] keyBytes() {
            return decode(secret);
        }

        private static byte[] decode(String secret) {
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(secret.trim());
            } catch (IllegalArgumentException cause) {
                throw new IllegalArgumentException("bahceden.jwt.secret Base64 kodlu olmalıdır");
            }
            if (bytes.length < MIN_KEY_BYTES) {
                throw new IllegalArgumentException(
                        "bahceden.jwt.secret en az " + MIN_KEY_BYTES + " bayt (256 bit) olmalıdır");
            }
            return bytes;
        }
    }
}
