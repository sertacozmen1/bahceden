package com.bahceden.common.web;

import com.bahceden.common.storage.FileType;
import java.io.InputStream;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Kişisel veri içeren dosyanın yanıt başlıkları tek yerde (ADR-045; satıcı
 * belgesi ve gönderi etiketi, add-shipments D4).
 *
 * <ul>
 *   <li>{@code Content-Type} içerikten tespit edilen türdür, istemcinin veya
 *       dış servisin bildirdiği değil;</li>
 *   <li>{@code X-Content-Type-Options: nosniff}: tarayıcı türü yeniden yorumlamaz;</li>
 *   <li>{@code Content-Security-Policy: sandbox}: dosya aynı kökenden açılsa bile
 *       içindeki betik çalışmaz;</li>
 *   <li>{@code Cache-Control: private, no-store}: kişisel veri ara bellekte kalmaz;</li>
 *   <li>dosya adı sunucuda üretilir.</li>
 * </ul>
 */
public final class ProtectedFileResponses {

    private ProtectedFileResponses() {
    }

    /**
     * @param servedName uzantısız, sunucunun seçtiği dosya adı
     */
    public static ResponseEntity<InputStreamResource> inline(FileType type, long sizeBytes, InputStream content,
                                                             String servedName) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type.contentType()))
                .contentLength(sizeBytes)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(servedName + "." + type.extension())
                        .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .body(new InputStreamResource(content));
    }
}
