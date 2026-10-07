# [ADR-030] Dil seçimi: çerez → `Accept-Language` → Türkçe; aynı dil `api`'ye iletilir

**Tarih:** 2026-10-07
**Durum:** Accepted
**Kaynak:** `add-i18n` design.md D2

---

## Context

Proje sahibi dili URL'de göstermemeyi seçti: `/giris` her dilde aynı adrestir. Dilin kalıcı tek yeri bu durumda tarayıcıdadır. Sayfa İngilizce iken `api`'nin hata mesajı Türkçe gelmemeli; iki tarafın aynı dili konuşması gerekir ([ADR-031](031-api-messages-by-accept-language.md)).

## Decision

- **Desteklenen diller:** `tr` (varsayılan) ve `en`.
- **Seçim sırası** (`web/src/i18n/locale.ts`, tek kaynak):
  1. `bahceden_locale` çerezi: kullanıcının açık seçimi, bir yıl, `SameSite=Lax`, oturum bilgisi taşımaz.
  2. Tarayıcının `Accept-Language` başlığı: ağırlıklara göre; bölge yok sayılır (`en-GB` → `en`); ağırlığı 0 olan dil kabul edilmez.
  3. Türkçe.
- **`<html lang>`** seçilen dille sunucuda basılır.
- **Dil menüsü** (`LocaleMenu`) başlıkta, tema menüsünün yanında tek ikondur.
  - Seçenekler kendi dilinde yazılır ("Türkçe", "English").
  - Seçim çereze yazılır ve sunucu bileşenleri yenilenir (`router.refresh()`); adres değişmez.
- **Başlık genişliği:** 375px'de dört öğe sığsın diye 420px altında marka yazısı ekran okuyucuya bırakılır; logo görünür kalır.
- **`api`'ye iletim:** `web` sunucu tarafında her `api` çağrısına seçili dili `Accept-Language` olarak ekler. Bunu yapan tek yer `lib/apiClient.ts`'tir. `api` tarayıcının başlığını değil `web`'in seçtiği dili görür.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Yalnızca `Accept-Language` | Kullanıcı tarayıcı dilinden farklı bir dili seçemez. |
| `localStorage` | Sunucu dili bilmez; ilk boyama yanlış dilde gelir ve betikle düzeltilir. |
| URL öneki (`/en/...`) | Proje sahibi öneksiz seçti. Bedeli: İngilizce sayfalar ayrı adreste indekslenmez. |
| Dil yalnızca hesapta | Oturumsuz ziyaretçiye çalışmaz. |

## Consequences

### Olumlu
- Adresler dilden bağımsız; paylaşılan bağlantı alıcının kendi dilinde açılır.
- Ekran ve sunucu mesajları her zaman aynı dilde.

### Olumsuz
- Arama motoru İngilizce sayfaları ayrı indekslemez; SEO yalnızca Türkçe içindir.
- Kök düzen çerez ve başlık okuduğu için her sayfa dinamik render edilir. Bugün zaten öyle (oturum alanı, tema).

### Nötr / Trade-off
- Başlıkta yer dar. Başlığa yeni öğe eklemek önce 375px genişliği kontrol etmeyi gerektirir.
