# [ADR-102] Oturum kapısının dönüş adresi sorguyu taşır; Next.js proxy isteğin tam adresini bir istek başlığına yazar

**Tarih:** 2026-10-21
**Durum:** Accepted
**Kaynak:** Hata düzeltmesi (branch `fix-gate-keeps-query`); proje sahibi proxy yaklaşımını onayladı (2026-10-21)

---

## Context

Korunan sayfalar oturumu `requireSignedIn` / `requireRole` ile denetler. Kapı çoğunlukla `layout.tsx` içinde çağrılır, çünkü yönlendirmenin yükleniyor sınırının dışında, gerçek bir HTTP 307 olması gerekir (`requireSession.ts`). Oturum yoksa kullanıcı `/login?next=`'e, erişim token'ı geçmişse `/session/refresh?next=`'e gider. Dönüş adresi kapıya sabit yol olarak veriliyordu (örn. `/admin/claims`).

Next.js layout'a sorgu dizesini vermez. Bu yüzden `/admin/claims?status=CLOSED` ya da `?page=2` açıkken oturum yenilenirse ya da giriş istenirse kullanıcı süzgeci ve sayfası kaybolmuş olarak döner. Dönüş adresini doğrulayan `safeReturnPath` sorguyu zaten kabul ediyordu; eksik olan, kapının sorguyu bilmesiydi.

[ADR-023](023-web-bff-cookies-and-header-csrf.md) ve [ADR-070](070-silent-session-refresh-on-any-page.md), oturum yenilemeyi ara katmanda (middleware) yapmayı reddetmişti: yenileme çerezi her isteğe giderdi ve paralel istekler yarışırdı.

## Decision

- `web/src/proxy.ts` (Next.js 16 proxy) yalnızca kapı kullanan yollarda çalışır: `/account`, `/admin`, `/seller`, `/cart`, `/checkout` ve altları.
  - İsteğin yolunu ve sorgusunu `x-bahceden-request-path` istek başlığına yazar. Next.js'in iç `_rsc` parametresi atılır.
  - İstemcinin gönderdiği aynı adlı başlık ezilir.
  - Çerezlere, token'a ve yanıta dokunmaz; oturum yenilemez. ADR-023 ve ADR-070'in reddettiği ara katman yenilemesi değildir.
- Kapı, başlığı yalnızca yönlendirme gerektiğinde (oturum yok ya da süresi dolmuş) okur. Dönüş adresi `returnPathFor(selfPath, başlık)` ile kurulur:
  - başlık `safeReturnPath` ile iç yol denetiminden geçer;
  - kapının korunan yolunun kendisi ya da altındaysa (`selfPath`, `selfPath/…`, `selfPath?…`) kullanılır;
  - değilse, yoksa ya da geçersizse `selfPath` kullanılır (eski davranış).
- Yenileme ucu ve giriş sayfası değişmez; `next` zaten `safeReturnPath` ile doğrulanıyor.

| Alternatif | Neden reddedildi |
|---|---|
| Kapıyı layout'tan sayfaya taşımak (sayfa `searchParams` alır) | Yönlendirme yükleniyor sınırının içinde kalır, gerçek 307 olmaz. Bu sorun daha önce boş sayfa hatasına yol açmıştı (`hesap-ve-oturum.md` 2026-10-09). |
| Sayfa düzeyinde ayrıca sorgulu kapı | Kapı önce layout'ta tetiklendiği için çoğu durumda etkisiz kalır. |
| `/session/refresh` sayfasında `document.referrer` | Sunucu yönlendirmesinde referrer önceki sayfadır, yönlendirilen adres değil. |
| Next.js iç başlıklarına (`next-url` vb.) güvenmek | Yalnızca istemci gezintilerinde gelir; belgelenmiş bir sözleşme değildir. |

## Consequences

### Olumlu
- Oturum yenilendiğinde ya da girişten sonra kullanıcı aynı süzgece ve sayfaya döner.
- Kapının başarılı yolu değişmez: oturum açıkken başlık okunmaz.

### Olumsuz
- Korunan yollardaki her istek bir proxy adımından geçer (bellekte başlık kopyası; ağ çağrısı yok).
- Yeni bir korunan yol eklenirse proxy `matcher`'ına da eklenmelidir. Eklenmezse o yolda dönüş adresi sorgusuz olur, güvenlik etkisi yoktur.

### Nötr / Trade-off
- Başlık istemciden de gelebilir, ama proxy onu ezer. Proxy çalışmasa bile kapı yalnızca kendi yolunun altındaki iç adrese döner; açık yönlendirme olmaz.
