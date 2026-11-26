# [ADR-108] Hız sınırı sayaçları Redis'te paylaşılır; Redis yoksa süreç içi yedekle fail-open

**Tarih:** 2026-11-26
**Durum:** Accepted · change `add-redis-cache-and-order-paid-event` D2 · [ADR-022](022-in-memory-fixed-window-rate-limiting.md)'yi *supersede* eder (politikalar, anahtarlar ve istemci adresi kuralı aynen geçerli; değişen yalnızca sayacın yeri)

---

## Context

[ADR-022](022-in-memory-fixed-window-rate-limiting.md) hız sınırını süreç belleğinde, sabit pencereyle kurdu ve bedelini yazdı: iki örnek çalışırsa sınır ikiye katlanır, yeniden başlatma sayaçları sıfırlar (`00-current-state.md` L9). Redis [ADR-107](107-redis-and-kafka-added-to-budget.md) ile bütçeye girdi; dağıtık hız sınırı onun iki kullanım yerinden biridir.

Kısıtlar:

- Politikalar (`RateLimitPolicy`) ve `RateLimiter.check(policy, keyPart...)` imzası sekiz çağıran tarafından kullanılıyor ve değişmemeli.
- Pencere davranışı bugün sabit saatle (`MutableClock`) test ediliyor; `SessionLoginTest` saati ileri sararak pencereyi kapatıyor. Redis'in kendi saatine bağlı bir TTL bu testleri gerçek beklemeye zorlar.
- Anahtar parçaları e-posta ve IP içerir (K10).

## Decision

1. **Sayaç Redis'tedir ve tek Lua betiğiyle atomik tutulur.**
   - Anahtar bir hash'tir: `s` pencere başlangıcı (ms), `c` sayı.
   - Betik "şimdi"yi ve pencere uzunluğunu argüman olarak alır. "Şimdi" uygulamanın enjekte edilen `Clock`'undan gelir.
   - Pencere yoksa veya `şimdi − s ≥ pencere` ise pencere 1'den yeniden başlar; değilse `HINCRBY` ile artar.
   - `PEXPIRE pencere` yalnızca çöp toplama içindir.
   - Semantik ADR-022 ile birebir aynıdır: pencere anahtarın ilk isteğiyle başlar.
2. **Anahtar:** `rl:{POLICY}:{sha256(parçalar)}`. Politika adı okunur kalır, e-posta ve IP Redis'e düz yazılmaz.
3. **Fail-open:**
   - Redis hatası (`DataAccessException`) isteği reddetmez. Aynı politika süreç içi yedek sayaçla uygulanır (`InMemoryWindowCounter`, ADR-022'nin kodu, değiştirilmeden taşındı).
   - Dakikada en çok bir `WARN` yazılır.
   - Zaman aşımları kısadır (komut 500 ms, bağlantı 1 sn): kesinti isteği uzun bekletmez.
4. **İmza ve politikalar değişmez.** Çağıran kod dokunulmadan kalır.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| `INCR` + ilk istekte `PEXPIRE` (Redis saatine bağlı pencere) | En yaygın desen, ama pencere Redis saatiyle kapanır: sabit saatli testler gerçek bekleme ister, saat sarılan testler (`SessionLoginTest`) kırılır |
| Epoch'a hizalı pencere (`floor(şimdi / pencere)` anahtarda) | Saatten beslenir ve basittir, ama pencere ilk istekle değil saat dilimiyle başlar. Sınırın hemen öncesi ve sonrası iki dolu pencere üst üste gelir (2N patlaması), semantik ADR-022'den sapar |
| Kayan pencere / token kovası | Daha düzgün dağılım ama daha karmaşık betik ve yeni testler; kimlik uçlarında fark yok |
| Fail-closed (Redis yoksa 429) | Redis kesintisi giriş ve kaydı tümden durdurur; hız sınırı yüzünden servis düşer |
| Bucket4j | Bütçe dışı ek paket; tek betik yetiyor |

## Consequences

### Olumlu
- L9 kapanır: sınır örnekler arasında toplamda uygulanır, yeniden başlatma sayaçları sıfırlamaz.
- Mevcut testler ve çağıranlar değişmedi; semantik aynı.
- Redis'te kişisel veri yok.

### Olumsuz
- Her korunan istek bir Redis gidiş-dönüşü ekler (~1 ms yerelde).
- Kesintide koruma örnek başına düşer (ADR-022'nin bugünkü düzeyi); yedek sayaçta biriken sayılar Redis dönünce birleşmez.
- Redis kesintisinde her istek bağlantı zaman aşımı kadar (en çok ~1 sn) gecikebilir.
- Pencere başlangıcı örneklerin saatinden gelir; örnekler arası saat kayması pencereyi o kadar kaydırır (NTP'li sunucularda ihmal edilebilir).

### Nötr / Trade-off
- ADR-022'nin istemci adresi kuralı (`forward-headers-strategy: native`) ve "adres güveni ağ düzenine bağlı" riski aynen geçerlidir (KALANLAR B5).
