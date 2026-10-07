# [ADR-031] `api` metinleri `MessageSource` ile, `Accept-Language`'a göre; hata kodları sabit

**Tarih:** 2026-10-07
**Durum:** Accepted
**Kaynak:** `add-i18n` design.md D3

---

## Context

`api` kullanıcıya gösterilebilir metin üretir: doğrulama mesajları, hata mesajları, bilgilendirmeler ve e-postalar (AGENTS.md §11.5: mesaj Türkçe ve tek kaynaktan). Bu metinler sabit olarak kod içine dağılmıştı. `web` sunucu mesajını olduğu gibi gösterir (AGENTS.md §12.3); bu yüzden sayfa İngilizce iken mesajların da İngilizce gelmesi gerekir.

## Decision

- **Mesaj dosyaları:** `messages.properties` (Türkçe, temel ve varsayılan) ve `messages_en.properties`.
  - Kodda kullanıcı metni yazılmaz, yalnızca anahtar yazılır (`error.invalidCredentials`, `validation.email.required` …).
  - `spring.messages.fallback-to-system-locale: false`: sunucunun işletim sistemi dili sonucu etkilemez.
- **Dil çözümleyici:** `AcceptHeaderLocaleResolver`, desteklenen diller `tr` ve `en`, varsayılan `tr`. Liste tek yerde: `SupportedLocale`.
- **`ApiException`** metin değil mesaj anahtarı taşır; metni `GlobalExceptionHandler` isteğin diliyle çözer.
- **Güvenlik filtresi:** 401 gövdesini yazan `ApiAuthenticationEntryPoint` `DispatcherServlet`'ten önce çalışır; dili aynı çözümleyiciden kendisi alır.
- **Bean Validation** mesajları `{anahtar}` biçimindedir; Spring Boot doğrulayıcısı bunları `MessageSource`'tan isteğin dilinde çözer.
- **Servisler dil bilmez:** istek bağlamına (`LocaleContextHolder`) dokunmaz. Controller isteğin dilini parametre olarak alır ve gerektiğinde açıkça geçirir; e-postanın dili hesabın dilidir ([ADR-032](032-account-locale-stored-and-refreshed-on-login.md)).
- **Sözleşme:** hata `code` değerleri dilden bağımsızdır ve değişmedi. Yapısal değişiklik yok; `ApiError.message` ve bilgilendirme alanlarının açıklamaları "isteğin dilinde" olarak güncellendi.
- **Bütünlük:** iki dosyanın anahtar kümesinin eşitliğini ve boş değer olmadığını `MessagesCatalogTest` zorunlu kılar.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| `api` Türkçe kalır, `web` koddan kendi metnini üretir | Alan bazlı doğrulama mesajları kodla ifade edilemiyor; metin iki yerde tutulur ve otorite `api`'den çıkar. |
| Dil sorgu parametresiyle (`?lang=en`) | HTTP standardı değil; her uca parametre eklenir. |
| Servislerde `LocaleContextHolder` | E-posta gibi akışlarda doğru dil isteğin değil hesabın dilidir; gizli bağlam testte JVM diline bağımlı kalır. |

## Consequences

### Olumlu
- Yeni paket yok (Spring çekirdeği).
- Metinler tek yerde; çeviri kod değişikliği gerektirmez.
- İstemci mantığı dilden bağımsız koda bağlı kalır.

### Olumsuz
- Argümanlı mesajlarda `MessageFormat` kuralı: kesme işareti `''` yazılır. Dosya başında not var.
- Log mesajları Türkçe kalır; operatör dili ile kullanıcı dili ayrıdır.

### Nötr / Trade-off
- OpenAPI açıklamaları (`@Schema`) Türkçe kalır: geliştirici dokümanıdır, kullanıcı metni değildir.
