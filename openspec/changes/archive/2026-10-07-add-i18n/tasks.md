# Tasks: add-i18n

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir (K6).
> **Bağımlılık onayı alındı (2026-10-07):** `next-intl` `web/` bütçesine eklenir (K7; ADR-029 ile kaydedilir).
> **Şema değişikliği onaylandı (2026-10-07):** `accounts.locale` (proje sahibi "hesabın dili saklansın" seçeneğini seçti).

## Faz 1 · `api`: mesaj kaynağı, istek dili, hesabın dili

- [x] 1.1 `common/i18n/`: desteklenen diller (`tr`, `en`; varsayılan `tr`), `AcceptHeaderLocaleResolver` bean'i; `application.yaml` `spring.messages` (temel ad, UTF-8, sistem diline düşme kapalı).
- [x] 1.2 `messages.properties` (Türkçe) + `messages_en.properties`: doğrulama, hata, bilgi mesajları ve e-posta konu/gövdeleri. Anahtar kümesi iki dosyada aynı.
- [x] 1.3 `ApiException` mesaj anahtarı taşır; `GlobalExceptionHandler` ve `ApiAuthenticationEntryPoint` metni isteğin diliyle çözer; `RateLimiter` anahtar kullanır.
- [x] 1.4 DTO ve controller doğrulama mesajları `{anahtar}` biçimine döner; yanıt mesajları (`RegistrationAccepted`, `LoginChallengeIssued`) isteğin dilinde.
- [x] 1.5 `005-account-locale.sql` (`--rollback` dahil) + `Account.locale`; kayıtta yazılır, parola adımında güncellenir; e-postalar hesabın dilinde.
- [x] 1.6 `ApiError.message` açıklaması güncellenir; `openapi.yaml` yeniden üretilir.
- [x] 1.7 **Test (`critical`):** İngilizce doğrulama hatası; dil yoksa ve desteklenmeyen dilde Türkçe; 401 gövdesi İngilizce; İngilizce kayıtta hesap dili + e-posta İngilizce; giriş dili günceller ve giriş kodu yeni dilde; "zaten var" bildirimi hesabın dilinde; iki mesaj dosyasının anahtar kümesi aynı.
- [x] 1.8 **Doğrulama:** `./mvnw -B verify` temiz.

## Faz 2 · `web`: next-intl, mesaj dosyaları, dil menüsü

- [x] 2.1 `next-intl` 4.14.9 pin'li eklenir; `next.config.ts` `next-intl/config` takma adı (**sapma S1:** eklenti sarmalayıcısı yerel ikili yüklediği için kullanılmadı, design.md D1); `src/i18n/locale.ts` (desteklenen diller, çerez, `Accept-Language` seçimi) ve `src/i18n/request.ts`; `AppConfig` tipi.
- [x] 2.2 `messages/tr.json` + `en.json`: `texts.ts`'teki tüm metinler ICU biçimine taşınır (çoğul, seçim, yer tutucu); `texts.ts` silinir.
- [x] 2.3 Kök düzen: `<html lang>`, `NextIntlClientProvider`, çevrilmiş `metadata`. Tüm sayfa, bileşen ve Route Handler'lar çeviri API'sine geçer.
- [x] 2.4 `money.ts` dil parametresi alır; `apiClient` seçili dili `Accept-Language` olarak iletir.
- [x] 2.5 `LocaleMenu` başlıkta tema menüsünün yanında; seçim çereze yazılır ve sayfa yenilenir; 420px altında marka yazısı `sr-only`.
- [x] 2.6 **Test:** dil seçimi (çerez > başlık > varsayılan, ağırlıklar, alt etiket); iki mesaj dosyasının anahtar kümesi aynı; ürün listesi İngilizce (çoğul ve para biçimi); dil menüsü çereze yazar ve yeniler; mevcut testler çeviri sağlayıcısıyla yeşil.
- [x] 2.7 **Doğrulama:** `lint`, `typecheck`, `test`, `build` temiz; `docker compose` üzerinde iki dilde vitrin ve hata mesajı uçtan uca denenir.

## Faz 3 · Dokümanlar ve kapanış

- [x] 3.1 ADR-029 (D1), ADR-030 (D2), ADR-031 (D3), ADR-032 (D4) + `adr/index.md` + `mkdocs.yml`.
- [x] 3.2 `KALANLAR.md` A4: çoklu dil kısmı karara bağlandı (ADR-029); bölge ve mobil soruları açık.
- [x] 3.3 `AGENTS.md`: §1 dil kuralı, §7 bütçe (`next-intl`), §11.5 mesaj kaynağı, §12.4 metin modülü, §15 A4 satırı.
- [x] 3.4 `docs/reference/`: `00-current-state.md`, `01-domain-model.md` (`accounts.locale`), `02-api-reference.md` (`Accept-Language`), `03-design-system.md` (dil menüsü); `docs/features/_genel.md` Tarihçe.
- [x] 3.5 Spec sync: `openspec/specs/localization/spec.md` oluşturuldu (4 gereksinim, 11 senaryo; delta ile birebir). Archive kullanıcı onayıyla yapıldı (2026-10-07).
