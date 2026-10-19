# Tasks: add-stale-record-cleanup

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir (K6).
> Yeni paket yok (K7). Şema yalnızca ekleyici bir kısmi indeksle değişir (`016`); sözleşme ve `web` etkilenmez.

## Faz 1 · `api`: ödenmeyen sipariş işi

- [x] 1.1 `BahcedenProperties.Cleanup` (`enabled`, `interval`, `pendingPaymentGrace`, `pendingPaymentBatchSize`, `tokenRetention`; pozitiflik denetimi); `application.yaml` (açıklamalı varsayılanlar), `application-test.yaml` (`enabled: false`).
- [x] 1.2 `OrderRepository`: süresi toleranstan önce dolmuş `PENDING_PAYMENT` kimlikleri (en eskiden, sayfalı) ve kimlikle `PESSIMISTIC_WRITE` kilit. Sorgu planı tam tarama gösterdi; `016-pending-payment-index.sql` kısmi indeksi eklendi.
- [x] 1.3 `CheckoutService`: ödeme dönüşündeki kapatma yolunu yapan taraf parametresiyle ortaklaştır; `expireUnpaid(orderId)` kilitler, durumu yeniden okur, sağlayıcıdan doğrular, sistem adına sonuçlandırır (D1).
- [x] 1.4 `PendingPaymentSweep` (tur, sipariş başına ayrı transaction, sağlayıcı hatasında atla) + `PendingPaymentSweepSchedule` (`bahceden.cleanup.enabled`).
- [x] 1.5 **Test (`critical`):**
  - sağlayıcı beklemede → `PAYMENT_EXPIRED`, satıcı siparişleri `VOID`, geçmişte yapan taraf boş, stok ve sepet değişmez;
  - sağlayıcı başarısız → `PAYMENT_FAILED`;
  - sağlayıcı başarılı → tam iade (`PAYMENT_EXPIRED` nedeni) ve `PAYMENT_EXPIRED`, stok düşmez;
  - tolerans dolmamış sipariş ve ödenmiş sipariş değişmez;
  - sağlayıcı hatası o siparişi değiştirmez, diğerini işler;
  - iş sonrası ödeme dönüşü sonuçlanmış siparişi döner (çift iade yok);
  - testlerde zamanlayıcı kurulmaz.
- [x] 1.6 **Doğrulama:** `./mvnw -B verify` temiz (334 test, 0 hata; 7 yeni `PendingPaymentSweepTest`). Test sağlayıcısına tek ödemeyi başarısız kılan `failVerifyOf` eklendi; `FakeShippingIntegratorTest` yeni ayar alanı için güncellendi.

## Faz 2 · `api`: süresi geçmiş token temizliği

- [x] 2.1 Dört repository'de toplu silme: `EmailVerificationTokenRepository`, `PasswordResetTokenRepository`, `LoginChallengeRepository` (`expires_at < kesim`); `RefreshTokenRepository` (ailesinin en geç süresi kesimden önce olan aileler) (D2).
- [x] 2.2 `ExpiredTokenCleanup` (tablo başına transaction, sayıları tek log satırında) + `ExpiredTokenCleanupSchedule` (`bahceden.cleanup.enabled`).
- [x] 2.3 **Test (`critical`):**
  - saklama payını geçmiş doğrulama, sıfırlama ve giriş kodu satırları silinir; pay içindekiler ve süresi dolmamışlar kalır;
  - canlı ailenin eski token'ı silinmez ve tekrar sunulduğunda aile iptal edilir;
  - süresi tümüyle geçmiş aile silinir; hesap silinmez.
- [x] 2.4 **Doğrulama:** `./mvnw -B verify` temiz (338 test, 0 hata; 4 yeni `ExpiredTokenCleanupTest`).

## Faz 3 · Uçtan uca doğrulama ve doküman

- [x] 3.1 `docker compose up -d --build`: ödenmemiş bir sipariş oluştur, süresini veritabanında geriye al → iş turunda `PAYMENT_EXPIRED`; süresi geçmiş token satırları → turda silinir. Log satırları kontrol edilir. (Dev'de `bahceden.cleanup.interval` PT1M yapıldı, `application-dev.yaml`. İlk tur süresi geçmiş doğrulama 10, sıfırlama 3, giriş kodu 3 satırı sildi; eklenen 8 günlük satır da silindi. `BHC-RZQEYPVQCN` önce toleransta olduğu için beklendi; süresi 2 saat geriye alınınca bir turda `PAYMENT_EXPIRED`, satıcı siparişi `VOID`, yapan taraf sistem; sahte sağlayıcı her ödemeyi onayladığı için tam iade kaydı oluştu.)
- [x] 3.2 ADR-096, ADR-097 + `docs/adr/index.md` + `docs/.tools/mkdocs.yml`.
- [x] 3.3 `docs/reference/01-domain-model.md` (silme davranışı), `docs/features/sepet-ve-siparis.md` ve `hesap-ve-oturum.md` (Tarihçe, sınırlar), `docs/features/index.md`, `docs/how-to/yerel-gelistirme.md` (yapılandırma notu), `00-current-state.md` (L15 ve L32 kalkar; tek örnek sınırı güncellenir; sayılar). `./docs/.tools/serve.sh --build` temiz. Ek: L38 tek örnek sınırı temizlik işlerini de kapsayacak şekilde genişletildi.
- [x] 3.4 Delta spec'ler ana spec'lere sync edilir.
- [x] 3.5 Archive kullanıcı onayıyla yapıldı (2026-10-20).
