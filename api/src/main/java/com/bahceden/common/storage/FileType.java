package com.bahceden.common.storage;

import java.util.Arrays;
import java.util.Optional;

/**
 * İçerikten tespit edilen dosya türleri (add-seller-onboarding D6).
 *
 * <p>Tür, dosyanın ilk baytlarındaki imzadan belirlenir; istemcinin bildirdiği
 * {@code Content-Type} ve dosya adı yok sayılır. Üç tür için birkaç bayt
 * karşılaştırması yeter; tür tespiti kütüphanesi (Tika) alınmaz (K7).
 */
public enum FileType {

    PDF("application/pdf", "pdf", new byte[] {'%', 'P', 'D', 'F', '-'}),

    JPEG("image/jpeg", "jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),

    PNG("image/png", "png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

    /** En uzun imzanın uzunluğu: tespit için okunması gereken bayt sayısı. */
    public static final int SIGNATURE_LENGTH = 8;

    private final String contentType;
    private final String extension;
    private final byte[] signature;

    FileType(String contentType, String extension, byte[] signature) {
        this.contentType = contentType;
        this.extension = extension;
        this.signature = signature;
    }

    public String contentType() {
        return contentType;
    }

    /** Sunulan dosyanın sunucunun ürettiği adında kullanılan uzantı. */
    public String extension() {
        return extension;
    }

    /**
     * @param head dosyanın ilk baytları (en az {@link #SIGNATURE_LENGTH} bayt
     *             okunmaya çalışılmış; dosya daha kısaysa daha az)
     */
    public static Optional<FileType> detect(byte[] head) {
        return Arrays.stream(values()).filter(type -> type.matches(head)).findFirst();
    }

    /** Saklanmış meta veriden geri okuma; veritabanı yalnızca bu değerleri tutar. */
    public static FileType fromContentType(String contentType) {
        return Arrays.stream(values())
                .filter(type -> type.contentType.equals(contentType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Bilinmeyen içerik türü: " + contentType));
    }

    private boolean matches(byte[] head) {
        if (head.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (head[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
