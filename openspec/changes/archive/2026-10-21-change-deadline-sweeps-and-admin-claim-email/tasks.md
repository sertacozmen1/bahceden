# Tasks: change-deadline-sweeps-and-admin-claim-email

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir (K6).
> Yeni paket yok (K7). Şema ve sözleşme değişmez; `web` etkilenmez.

## Faz 1 · `api`: süre işleri hatalı kaydı atlayıp devam eder (D1)

- [x] 1.1 `DefectClaimRepository`: süresi dolmuş talepleri (kimlik, süre) projeksiyonuyla ve "sonrası" koşuluyla okuyan sorgu; ilk sayfa için koşulsuz biçim. Mevcut `findOverdueIds` yerini alır. (`findOverdue` + `findOverdueAfter`; projeksiyon `order/SweepCursor`.)
- [x] 1.2 `DefectClaimSweep.sweepOnce`: sayfa döngüsü, imleç son okunan (süre, kimlik); tur sonu sayım logu.
- [x] 1.3 `OrderRepository`: aynı biçim `findIdsExpiredBefore` için; `PendingPaymentSweep.sweepOnce` sayfa döngüsü. (`findExpiredBefore` + `findExpiredBeforeAfter`.)
- [x] 1.4 `application.yaml` açıklamaları: iki ayar artık sayfa boyutudur. Testte sayfa boyutu küçük verilir (`application-test.yaml`: 2). `BahcedenProperties` Javadoc'u da güncellendi.
- [x] 1.5 **Test (`critical`):** (`ScriptedPaymentProvider`'a ödeme bazında iade reddi ve deneme sayaçları eklendi: `failRefundsOf`, `refundAttemptsOf`, `verifyCallsOf`.)
  - `DefectClaimSweepTest`: sayfa boyutu 2 iken iadesi başarısız olan iki talep, arkadaki talebin aynı turda işlenmesini engellemez; hatalılar `OFFERED` kalır ve sonraki turda yeniden denenir; bir turda her talep en çok bir kez denenir;
  - `PendingPaymentSweepTest`: aynı senaryo, sağlayıcı hatası veren siparişlerle.
- [x] 1.6 **Doğrulama:** `./mvnw -B verify` temiz (345 test, 0 hata; 2 yeni test; test sayfa boyutu 2 ile mevcut süre işi testleri de yeşil).

## Faz 2 · `api`: yönetime düşen talepte admin e-postası (D2)

- [x] 2.1 `AccountRepository.findByRole(AccountRole)`.
- [x] 2.2 `OrderNotifier.claimEscalated(claim, transition)`: alıcı ve satıcıya ek olarak her admin hesabına, kendi dilinde; neden geçişten gelir. İki çağıran güncellenir.
- [x] 2.3 `messages.properties` + `messages_en.properties`: `mail.claimEscalated.adminSubject`, `mail.claimEscalated.adminBody`, iki neden metni. `MessagesCatalogTest` yeşil.
- [x] 2.4 **Test (`critical`):** (`DefectClaimSweepTest.satici_sessiz_admin_eposta`, `DefectClaimResponseTest.yonetime_tasima_admin_eposta`; admin yokken iki e-posta `satici_sessiz` ve `yonetime_tasima`'da sabit. `RecordingMailSender.onlySentTo` eklendi.)
  - süre dolunca ve alıcı taşıyınca her admin'e, kendi dilinde, nedeni ve `/admin/claims/{id}` bağlantısını içeren e-posta;
  - admin olmayan hesaba gitmez;
  - admin yoksa alıcı ve satıcı e-postaları yine gider;
  - mevcut escalation testleri değişmeden yeşil: kurulumlarında admin hesabı yok, beklenti gevşetilmedi.
- [x] 2.5 **Doğrulama:** `./mvnw -B verify` temiz (347 test, 0 hata; 2 yeni test).

## Faz 3 · Uçtan uca doğrulama ve doküman

- [x] 3.1 `docker compose up -d --build`: dev admin hesabıyla bir talebi yönetime taşı → mailpit'te admin e-postası (neden, bağlantı); bağlantı admin talep detayını açar. (Dev siparişi `BHC-6GZBBKGC98`, satıcı siparişi 8: alıcı talep açtı (201), üretici reddetti (200), alıcı yönetime taşıdı (200). Mailpit: `admin@ornek.test`'e "karar bekleyen sorun bildirimi", gövdede "alıcı bildirimi yönetime taşıdı" ve `/admin/claims/2`; alıcı ve üreticiye mevcut e-postalar. Bağlantı admin girişinden sonra talep detayını açtı. Süre dolma yolu ve İngilizce gövde testlerde sabit. Talep 2 dev veritabanında `ESCALATED` kaldı.)
- [x] 3.2 ADR-100, ADR-101 + `docs/adr/index.md` + `docs/.tools/mkdocs.yml`; ADR-083, ADR-085, ADR-096'ya not.
- [x] 3.3 `docs/features/ayipli-urun-ve-iade.md`, `sepet-ve-siparis.md` (Tarihçe; bilinçli sınırlar), `docs/features/index.md`, `docs/how-to/admin-yetkisi-verme.md` notu, `00-current-state.md` (L37 ve L40 kalkar, test sayısı, sıradaki adım). `./docs/.tools/serve.sh --build` temiz.
- [x] 3.4 Delta spec'ler ana spec'lere sync edilir.
- [x] 3.5 Archive kullanıcı onayıyla yapıldı (2026-10-21).
