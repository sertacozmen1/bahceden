package com.bahceden.seller;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Belge yanıtının başlıkları tek yerde (add-seller-onboarding D6): sahibin ve
 * admin'in belge uçları aynı korumayı kullanır.
 *
 * <ul>
 *   <li>{@code Content-Type} içerikten tespit edilen türdür, istemcinin
 *       gönderdiği değil;</li>
 *   <li>{@code X-Content-Type-Options: nosniff}: tarayıcı türü yeniden yorumlamaz;</li>
 *   <li>{@code Content-Security-Policy: sandbox}: belge aynı kökenden açılsa bile
 *       içindeki betik çalışmaz;</li>
 *   <li>{@code Cache-Control: private, no-store}: kişisel veri ara bellekte kalmaz;</li>
 *   <li>dosya adı sunucuda üretilir; kullanıcının verdiği ad hiçbir yerde yok.</li>
 * </ul>
 */
final class SellerDocumentResponses {

    private static final String SERVED_NAME = "belge";

    private SellerDocumentResponses() {
    }

    static ResponseEntity<InputStreamResource> inline(SellerDocumentContent document) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.type().contentType()))
                .contentLength(document.sizeBytes())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(SERVED_NAME + "." + document.type().extension())
                        .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .body(new InputStreamResource(document.content()));
    }
}
