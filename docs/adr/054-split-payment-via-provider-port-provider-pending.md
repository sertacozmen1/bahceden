# [ADR-054] Ödeme bölünmüş ödeme (alt üye işyeri) modeliyle alınır; sağlayıcı bir arayüzün arkasında, seçimi sonraya kalır

**Tarih:** 2026-10-08
**Durum:** Accepted
**Kaynak:** `openspec/KALANLAR.md` A1 (ödeme sağlayıcısı ve payout modeli); ürün sahibinin 2026-10-08 kararı

---

## Context

İlanlar yayında; sepet, sipariş ve ödeme dilimleri A1 kararını bekliyor. İki ayrı soru var:

1. **Para nasıl akar?** Alıcının ödediği para satıcıya ve pazaryerine nasıl bölünür?
2. **Hangi sağlayıcı?** Sanal POS / ödeme kuruluşu hangisi?

Para akışı hukuki bir sorudur. 6493 sayılı Ödeme ve Menkul Kıymet Mutabakat Sistemleri, Ödeme Hizmetleri ve Elektronik Para Kuruluşları Kanunu, banka ve ödeme hizmeti sağlayıcısı dışındaki kişilerin ödeme hizmeti sunmasını yasaklar. İzinsiz faaliyete hapis ve adli para cezası öngörür. Alıcının parasını pazaryerinin kontrol ettiği bir havuz hesapta toplayıp satıcılara dağıtmak, hukuk yorumlarında lisans ihtiyacını doğurabilecek en önemli unsur olarak geçiyor. Lisanslı ödeme kuruluşlarının "pazaryeri" ürünleri (iyzico Pazaryeri, PayTR Pazaryeri) bu yüzden var: satıcı sağlayıcıda *alt üye işyeri* olarak tanımlanır, ödeme anında satıcı payı ve pazaryeri payı ayrılır, parayı satıcıya sağlayıcı öder.

Sağlayıcı seçimi ise ticari bir sözleşme konusudur: komisyon oranları teklif usulüyle belirleniyor, başvuru ve onay süreleri değişken. Ürün sahibi bu seçimi sonraya bıraktı.

Bugün `api` hiçbir dış ödeme servisine bağlı değil. `AGENTS.md` K10 geliştirme ve testte gerçek sağlayıcıya gidilmesini yasaklar; zaten bir `fake` sağlayıcı gerekiyor.

## Decision

1. **Para akışı bölünmüş ödemedir (alt üye işyeri modeli).** Alıcının ödemesi sağlayıcıda tahsil edilir; satıcı payı ve pazaryeri payı ödeme isteğinde ayrılır; parayı satıcıya sağlayıcı aktarır. Bahçeden alıcı parasını hiçbir hesapta tutmaz ve satıcıya elle ödeme yapmaz. Sistem yalnızca her satıcı payının (hakediş) kaydını tutar.
2. **Satıcı satış yapabilmek için ödeme bilgilerini tamamlar.** Alt üye işyeri kaydı için gereken bilgiler (IBAN, satıcı türüne göre TCKN veya VKN, yasal ad) başvurudan ayrı bir adımda alınır. Bu, [ADR-037](037-seller-application-with-document-and-manual-approval.md) §4'ün öngördüğü "IBAN, A1 kararıyla ayrı bir change'de gelir" yoludur. Ödeme bilgisi eksik satıcının ilanı satın alınamaz.
3. **Sağlayıcı `payment` domaininde tek bir arayüzün arkasındadır.** Arayüz bu modelin ihtiyacı kadardır: alt üye kaydı, bölünmüş ödeme başlatma ve sonucunu doğrulama, iade. Geliştirme ve testte `fake` uygulama çalışır (K10). Gerçek uygulama sağlayıcı sözleşmesi imzalanınca eklenir; ödeme çağrıları Java'nın HTTP istemcisiyle yapılır, sağlayıcı SDK'sı K7 onayı olmadan alınmaz.
4. **Sağlayıcı seçimi açık kalır** (KALANLAR A1'de). Aday sağlayıcılar alt üye işyeri modelini destekleyen lisanslı ödeme kuruluşlarıdır.

**Değerlendirilen ve reddedilen alternatifler:**

| Alternatif | Neden reddedildi |
|---|---|
| Havuz hesap + manuel hakediş | Teknik olarak en basiti. Ama alıcı parasını pazaryeri hesabında tutup dağıtmak 6493 kapsamında lisanssız ödeme hizmeti riski taşır. Hukuk görüşü olmadan kabul edilemez. Hakediş ödemeleri de elle yapılır. |
| Sağlayıcıyı şimdi seçmek (iyzico Pazaryeri) | Açık API dokümanı ve sandbox'ı var; teknik olarak en olası aday. Ama sözleşme ve oran teklif usulüyle gelir, ürün sahibi seçimi ticari görüşmeye bıraktı. Arayüz bu seçimi geri dönüşü ucuz tutar. |
| Arayüz olmadan doğrudan sağlayıcı koduna bağlanmak | Sağlayıcı seçilmeden kod yazılamaz; test ve geliştirme için `fake` yine gerekir. |
| Birden fazla sağlayıcıyı aynı anda desteklemek | §15 iki ödeme akışını birlikte uygulamayı yasaklar; §3 bugün gerekmeyen soyutlamayı yasaklar. Arayüzün tek gerçek uygulaması olur. |

## Consequences

### Olumlu
- Bahçeden alıcı parası tutmaz; lisans riski sağlayıcının lisansıyla karşılanır.
- Satıcıya ödeme ve hakediş dağıtımı sağlayıcıda otomatik; elle EFT ve mutabakat yok.
- Sipariş ve ödeme durum makinesi, sağlayıcı seçilmeden `fake` ile uçtan uca kurulup test edilebilir.

### Olumsuz
- Satıcıdan yeni kişisel veri alınır: IBAN ve TCKN veya VKN (KVKK, K10). Bu veriler maskeli loglanır, yalnızca sahibi ve admin görür ve sağlayıcıya iletilir. Dış servise kişisel veri göndermek §9 gereği bu kararla onaylanmış sayılır; aydınlatma metni A5'e bağlıdır.
- Sağlayıcının alt üye onayı, vadesi ve blokajı satıcının parasına ne zaman ulaşacağını belirler; bu süre Bahçeden'in kontrolünde değildir.
- Gerçek sağlayıcı bağlanana kadar canlıda ödeme alınamaz. Üretime çıkış sağlayıcı sözleşmesine bağlıdır.

### Nötr / Trade-off
- Arayüz tek gerçek uygulamaya göre şekillenir. Seçilen sağlayıcının API'si arayüzü değiştirirse bu bir change konusudur.
- Komisyon ([ADR-055](055-commission-percent-plus-fixed-from-seller.md)) ve iade kuralı ([ADR-056](056-cancel-before-shipment-and-defect-refund.md)) bu modelin içinde hesaplanır.
