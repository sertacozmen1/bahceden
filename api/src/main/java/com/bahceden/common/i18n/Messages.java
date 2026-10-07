package com.bahceden.common.i18n;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Kullanıcıya dönen metinlerin tek kaynağı (design.md D3, ADR-031).
 *
 * <p>Metinler {@code messages.properties} (Türkçe, temel) ve
 * {@code messages_en.properties} dosyalarındadır; kodda kullanıcı metni
 * yazılmaz, yalnızca anahtar yazılır. Dil her zaman açıkça verilir: servisler
 * istek bağlamına ({@code LocaleContextHolder}) dokunmaz, çünkü e-posta gibi
 * akışlarda doğru dil isteğin değil hesabın dilidir (D4).
 *
 * <p>Argümanlı mesajlarda {@link java.text.MessageFormat} kuralı geçerlidir:
 * metindeki kesme işareti {@code ''} olarak yazılır.
 */
@Component
public class Messages {

    private final MessageSource messageSource;

    Messages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String key, SupportedLocale locale, Object... args) {
        return messageSource.getMessage(key, args.length == 0 ? null : args, locale.locale());
    }
}
