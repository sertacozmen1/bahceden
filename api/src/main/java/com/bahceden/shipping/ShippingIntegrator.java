package com.bahceden.shipping;

import com.bahceden.shipping.domain.ShipmentStatus;

/**
 * Kargo entegratörü (ADR-057, add-shipments D1).
 *
 * <p>Arayüz gönderi akışının ihtiyacı kadardır: gönderi oluştur, etiket al,
 * durum sorgula, iptal et. Entegratör seçilene kadar (KALANLAR A2) tek uygulama
 * {@link FakeShippingIntegrator}'dır; gerçek uygulama sözleşme imzalanınca
 * Java HTTP istemcisiyle, SDK'sız eklenir (K7).
 *
 * <p>Her metot entegratöre ulaşılamazsa veya istek reddedilirse
 * {@link ShippingIntegratorException} fırlatır.
 */
public interface ShippingIntegrator {

    /** Entegratörde gönderi açar. */
    CreatedShipment create(ShipmentRequest request);

    /** Gönderinin etiketi; içerik türü çağıran tarafından içerikten doğrulanır (D4). */
    byte[] label(String integratorRef);

    /** Gönderinin entegratördeki güncel durumu. */
    ShipmentStatus status(String integratorRef);

    /** Kargoya verilmemiş gönderiyi iptal eder; verilmişse reddeder (D6). */
    void cancel(String integratorRef);

    /**
     * Gönderi isteği. Alıcı bilgileri kişisel veridir; entegratöre iletilmesi
     * ADR-057 §4 ile onaylıdır, log'a yazılmaz (K10).
     *
     * @param reference satıcı siparişinin tekrar korumalı referansı ({@code BHC-<id>-<n>})
     * @param sender    gönderici; paket şubeye teslim edilir, açık adres yoktur
     * @param recipient alıcı ve teslimat adresi
     * @param desi      koli boyutunun desi karşılığı
     */
    record ShipmentRequest(String reference, Sender sender, Recipient recipient, int desi) {
    }

    /**
     * @param name         satıcının görünen adı
     * @param provinceName satıcının ili
     * @param phone        satıcının telefonu
     */
    record Sender(String name, String provinceName, String phone) {
    }

    record Recipient(String name, String phone, String provinceName, String district, String addressLine,
                     String postalCode) {
    }

    /**
     * @param integratorRef  entegratörün gönderi anahtarı
     * @param carrier        gönderiyi taşıyacak kargo firması
     * @param trackingNumber takip numarası (şubede gönderi kodu olarak da kullanılır)
     */
    record CreatedShipment(String integratorRef, String carrier, String trackingNumber) {
    }
}
