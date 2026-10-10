package com.bahceden.seller;

import com.bahceden.common.web.ProtectedFileResponses;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;

/**
 * Belge yanıtı (add-seller-onboarding D6): sahibin ve admin'in belge uçları
 * aynı korumayı kullanır. Başlıklar {@link ProtectedFileResponses}'ta; gönderi
 * etiketi de aynı korumayla sunulur (add-shipments D4).
 */
final class SellerDocumentResponses {

    private static final String SERVED_NAME = "belge";

    private SellerDocumentResponses() {
    }

    static ResponseEntity<InputStreamResource> inline(SellerDocumentContent document) {
        return ProtectedFileResponses.inline(document.type(), document.sizeBytes(), document.content(), SERVED_NAME);
    }
}
