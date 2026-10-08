package com.bahceden.seller;

import com.bahceden.common.storage.FileType;
import java.io.InputStream;

/**
 * Sunulmak üzere açılmış bir belge: içerikten tespit edilmiş tür, boyut ve
 * akış. Akışı controller yanıta yazar ve kapatır.
 */
public record SellerDocumentContent(FileType type, long sizeBytes, InputStream content) {
}
