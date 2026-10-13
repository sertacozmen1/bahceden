## MODIFIED Requirements

### Requirement: Alıcı kendi siparişlerini görür

Sistem, alıcının kendi siparişlerini en yeniden eskiye, sayfalı olarak listelemesini SHALL sağlar. Alıcı her siparişin detayını da görür. Detay şunları içerir:

- sipariş numarası ve durumu;
- teslimat adresi;
- satıcılara göre bölünmüş satırlar: ürün adı, adet, birim fiyat, satır tutarı;
- her satıcı siparişinin kargo ücreti ve durumu;
- kargoya verme son tarihi;
- gönderi açılmışsa her satıcı siparişinin kargo firması, takip numarası, gönderi durumu, kargoya verilme ve teslim zamanı;
- teslim edilmiş satıcı siparişinde ayıplı ürün talebi açılabilir mi ve son anı;
- talep varsa talebin durumu, satırları ve adetleri (istenen, önerilen, iade edilen), fotoğrafları, satıcının gerekçesi, alıcının son tarihi ve iade tutarı;
- genel toplam.

Komisyon ve satıcı hakedişi alıcıya gösterilmez. Gönderi etiketi alıcıya sunulmaz.

#### Scenario: Siparişlerim
- **WHEN** alıcı sipariş listesini ister
- **THEN** yalnızca kendi siparişleri ortak liste şeklinde döner

#### Scenario: Başkasının siparişi
- **WHEN** alıcı başka bir hesabın sipariş numarasıyla detay ister
- **THEN** 404 döner ve siparişin varlığı açığa çıkmaz

#### Scenario: Oturum olmadan
- **WHEN** kimlik bilgisi olmadan sipariş listesi veya detayı istenir
- **THEN** istek 401 ile reddedilir

#### Scenario: Kargodaki sipariş
- **WHEN** alıcı kargoya verilmiş bir satıcı siparişi olan siparişinin detayını ister
- **THEN** o satıcı siparişinde kargo firması, takip numarası, gönderi durumu ve kargoya verilme zamanı bulunur

#### Scenario: Talep açılabilir sipariş
- **WHEN** alıcı teslimden 10 saat sonra siparişinin detayını ister
- **THEN** teslim edilen satıcı siparişinde talep açılabildiği ve talep açmanın son anı bulunur

### Requirement: Satıcı kendi satıcı siparişlerini görür

Sistem, onaylı satıcının yalnızca kendisine ait satıcı siparişlerini en yeniden eskiye, sayfalı ve durumla süzülebilir biçimde listelemesini SHALL sağlar. Süzme `NEW`, `SHIPPED`, `DELIVERED`, `DELIVERY_FAILED`, `REFUNDED`, `CLOSED` ve `CANCELLED` durumlarını kabul eder. Satıcı her satıcı siparişinin detayını da görür. Detay şunları içerir:

- satırlar;
- ürün tutarı;
- komisyon ve satıcı hakedişi; ayıplı ürün iadesi yapıldıysa iadeden sonraki komisyon ve hakediş;
- kargoya verme son tarihi;
- alıcı adresinden yalnızca il ve ilçe;
- gönderi açılmışsa kargo firması, takip numarası, gönderi durumu ve koli boyutu; etiket ayrı ve yalnızca satıcıya açık bir uçtan alınır;
- talep varsa talebin durumu, satırları ve adetleri, alıcının açıklaması, fotoğrafları, satıcının son tarihi ve sonucu.

Ödemesi tamamlanmamış satıcı siparişleri satıcıya gösterilmez. Her işlem önce onaylı satıcı kapısından, sonra sorgu seviyesindeki sahiplik denetiminden geçer.

#### Scenario: Gelen siparişler
- **WHEN** onaylı satıcı sipariş listesini ister
- **THEN** yalnızca kendi ödenmiş satıcı siparişleri döner; ödeme bekleyen veya ödemesi başarısız siparişler görünmez

#### Scenario: Alıcı adresi gizliliği
- **WHEN** satıcı bir satıcı siparişinin detayını ister
- **THEN** yanıtta alıcının il ve ilçesi bulunur; adı, telefonu ve açık adresi bulunmaz

#### Scenario: Başka satıcının siparişi
- **WHEN** satıcı başka bir satıcının satıcı siparişini ister veya iptal etmeye çalışır
- **THEN** istek 404 ile reddedilir

#### Scenario: Onaylı olmayan satıcı
- **WHEN** askıdaki veya onaylı olmayan bir satıcı sipariş uçlarından birini çağırır
- **THEN** istek 403 ile reddedilir

#### Scenario: Kargodakiler süzülür
- **WHEN** satıcı listeyi `SHIPPED` durumuyla süzer
- **THEN** yalnızca kargoya verilmiş ve henüz teslim edilmemiş satıcı siparişleri döner

#### Scenario: İade sonrası hakediş
- **WHEN** kısmi iadeyle sonuçlanmış talebi olan satıcı siparişinin detayı istenir
- **THEN** yanıtta asıl komisyon ve hakedişin yanında iadeden sonraki komisyon ve hakediş bulunur
