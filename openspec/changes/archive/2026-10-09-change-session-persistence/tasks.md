# Tasks: change-session-persistence

> Her faz **tek başına çalışır durumda** bırakılır (`AGENTS.md` K5). Kutu yalnızca gerçekten biten iş için işaretlenir (K6).
> Yeni paket yok (K7). Şema ve sözleşme değişmez.

## Faz 1 · `api`: tolerans penceresi (D1)

- [x] 1.1 `RefreshToken.usedWithin`; `SessionService.REUSE_GRACE` (30 sn) ve `refresh` içinde pencere dalı (iptal ve süre denetiminden sonra, aile iptalinden önce).
- [x] 1.2 **Test (`critical`, `SessionRefreshTest`):**
  - pencerede tekrar kullanım yeni çift verir ve iki dal da geçerli kalır;
  - pencere tekrar kullanımla uzamaz;
  - çıkıştan sonra pencerede de red;
  - pencere dışında aile iptali (mevcut testler pencereyi geçecek şekilde güncellendi).
- [x] 1.3 **Doğrulama:** `./mvnw -B verify` temiz.

## Faz 2 · `web`: sessiz yenileme ve sekme kilidi (D2, D3)

- [x] 2.1 `/api/auth/refresh`: `silent=1` kipi (başarısızlıkta aynı sayfaya oturumsuz dönüş; `api`'ye ulaşılamazsa oturum silinmeden dönüş).
- [x] 2.2 `startSessionRefresh` (Web Locks; kilit doluysa bekleyip dönüş yoluna gitme) ve `SessionRefreshRedirect`'in bunu kullanması.
- [x] 2.3 `SessionAutoRefresh`: `SessionNav` süresi dolmuş durumda render eder; ara sayfada çalışmaz; dakikada bir deneme (`sessionStorage` zaman damgası).
- [x] 2.4 **Test:** route (normal ve sessiz kip, çerez yok, `api` erişilemez, site dışı dönüş yolu); `SessionAutoRefresh` (gezinti adresi, döngü koruması, ara sayfa, başka sekme kilidi).
- [x] 2.5 **Doğrulama:** `lint`, `typecheck`, `test`, `build` temiz.

## Faz 3 · Uçtan uca doğrulama ve doküman

- [x] 3.1 `docker compose up --build`: giriş → erişim token'ı dolmuş oturumla ana sayfa açılır → başlıkta maskeli e-posta görünür; yenileme token'ı geçersizken ana sayfa oturumsuz açılır (giriş ekranına gitmez).
- [x] 3.2 ADR-069, ADR-070 + `docs/adr/index.md` + mkdocs nav; ADR-018 ve ADR-023 durum notları.
- [x] 3.3 `docs/reference/02-api-reference.md`, `00-current-state.md`, `docs/features/hesap-ve-oturum.md` *Tarihçe*.
- [x] 3.4 Delta spec ana spec'e birleştirildi (sync).
- [x] 3.5 Docs strict derleme temiz.
