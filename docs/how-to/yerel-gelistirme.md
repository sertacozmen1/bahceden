# Nasıl Yapılır: Yerel geliştirme ortamını kurma

**Özet:** Bu rehber, Bahçeden deposunu yeni bir makinede çalışır hale getirmek için gereken araçları ve doğrulama adımlarını gösterir. Rehber, 2026-10-06 tarihinde kurulumu yapılmış bir Windows 11 makinesindeki gerçek adımlara dayanır.

> [!NOTE]
> Bugün depoda yalnızca kurallar ve dokümantasyon var; `api/` ve `web/` henüz yazılmadı ([güncel durum](../reference/00-current-state.md)). Bu rehberin "Uygulamayı çalıştırma" bölümü ilk dikey dilimle birlikte doldurulacaktır. Araç kurulumu bölümü bugün geçerlidir.

## Ön koşullar

| Araç | Sürüm | Neden |
|---|---|---|
| **JDK 21 (LTS)** | 21.x | `api/` [ADR-001](../adr/001-spring-api-and-next-web.md) ile Java 21 üzerinde çalışır. 24 gibi LTS olmayan sürümler proje sürümü değildir. |
| **Maven** | 3.9.x | Derleme. Ayrı kurmak **gerekmez**: IntelliJ IDEA 3.9.x bundle eder ve wrapper üretildikten sonra `./mvnw` yeterlidir. |
| **Node.js** | 20+ | `web/` ([ADR-002](../adr/002-dependency-budget.md)). |
| **Docker + Compose** | Engine 24+, Compose v2 | PostgreSQL, `mailpit` ve Testcontainers testleri ([ADR-005](../adr/005-test-strategy.md)). |
| **Git** | 2.40+ | Depo ve hook'lar. |

---

## Adımlar

### 1. Depoyu klonlayın ve hook'ları açın

```bash
git clone https://github.com/sertacozmen1/bahceden.git
cd bahceden
git config core.hooksPath .githooks
```

Hook, commit öncesi değişen katmanın testlerini koşar. Acil durumda `git commit --no-verify` ile atlanabilir; atlanan test bir sonraki commit'te koşulmak zorundadır.

### 2. JDK 21'i kurun

JDK'yı IntelliJ'in kullandığı dizine kurmak en az sürtünmeli yoldur: IDE orayı otomatik tarar ve yönetici izni gerekmez.

**IntelliJ ile (önerilen):** `File → Project Structure → SDKs → + → Download JDK` → Version `21`, Vendor `Amazon Corretto` → Download. JDK `~/.jdks/` altına iner.

**Komut satırı ile (Windows, yönetici izni gerekmez):**

```powershell
$ProgressPreference='SilentlyContinue'
Invoke-WebRequest -Uri 'https://corretto.aws/downloads/latest/amazon-corretto-21-x64-windows-jdk.zip' -OutFile "$env:TEMP\corretto21.zip"
Expand-Archive "$env:TEMP\corretto21.zip" -DestinationPath "$env:TEMP\corretto21"
# Zip içindeki jdk dizinini ~/.jdks altına taşıyın, örn:
#   Move-Item "$env:TEMP\corretto21\jdk21.0.12.1" "$env:USERPROFILE\.jdks\corretto-21.0.12.1"
```

Doğrulama:

```bash
"$HOME/.jdks/corretto-21.0.12.1/bin/javac" -version   # javac 21.0.12.1
```

> [!NOTE]
> Kalıcı `JAVA_HOME` ve `PATH` ayarı **zorunlu değildir** ve bu makinede bilinçli olarak yapılmamıştır: JDK seçimi proje başına IntelliJ üzerinden yönetilir, container'lar kendi JDK imajını taşır. Birden fazla JDK yan yana durabilir (`~/.jdks` altında 21 ve 24 birlikte duruyor).

### 3. Maven'ı doğrulayın (kurmayın)

IntelliJ'in bundle ettiği Maven'ı kullanın:

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\corretto-21.0.12.1"
& "C:\Program Files\JetBrains\IntelliJ IDEA <sürüm>\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" -v
```

Beklenen çıktı: `Apache Maven 3.9.x` ve `Java version: 21.x`.

`api/` oluşturulduktan sonra wrapper bir kez üretilir (`mvn wrapper:wrapper`) ve depoya girer; o andan itibaren herkes `./mvnw` kullanır, sistemde Maven aranmaz.

### 4. Node ve Docker'ı doğrulayın

```bash
node -v            # v20 veya üstü
npm -v
docker info --format 'Server: {{.ServerVersion}} / {{.OSType}}'
docker compose version
```

> [!TIP]
> Docker Desktop kapalıysa `docker info` şu hatayı verir: `failed to connect to the docker API at npipe:...`. Docker Desktop'ı başlatıp birkaç saniye bekleyin.

### 5. Veritabanını IntelliJ'den görün

Ortam ayaktayken PostgreSQL `localhost:5432` üzerinden erişilebilir. IntelliJ'in **Database** aracıyla şemayı, tabloları ve Liquibase geçmişini doğrudan görebilirsiniz.

**Bağlantı değerleri** (yalnızca yerel geliştirme; `docker-compose.yml` içinde tanımlı):

| Alan | Değer |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | `bahceden` |
| User | `bahceden` |
| Password | `bahceden-dev` |

**IntelliJ'de ekleme:** `View → Tool Windows → Database` → `+` → `Data Source` → `PostgreSQL` → yukarıdaki değerler → `Test Connection` → `OK`. Sürücü ilk seferde indirilir.

Bağlantı eklendikten sonra `public` şemasına sağ tıklayıp **Refresh** ile tabloları yükleyin. Göreceğiniz tablolar:

| Tablo | İçerik |
|---|---|
| `sellers`, `products` | Katalog ([domain modeli](../reference/01-domain-model.md)) |
| `databasechangelog` | Uygulanmış Liquibase changeset'leri: hangi değişiklik ne zaman koştu, hangi bağlamla ([ADR-016](../adr/016-liquibase-for-schema-migrations.md)) |
| `databasechangeloglock` | Liquibase kilit tablosu; elle dokunulmaz |

> [!TIP]
> `databasechangelog` tablosundaki `contexts` kolonu, geliştirme seed'inin yalnızca `dev` bağlamında uygulandığını gösterir. Bir changeset'in neden koşmadığını anlamak için önce buraya bakın.

> [!WARNING]
> Bu kimlik bilgileri **yalnızca yerel geliştirme** içindir ve bilinçli olarak depodadır. Üretim veritabanına bağlanmak için aynı yöntemi kullanmayın: üretim kimlik bilgileri ne depoya ne de AI asistanının bağlamına girer (`AGENTS.md` K10).

Şema değişikliği **elle yapılmaz**: tabloyu IDE üzerinden değiştirmek Liquibase geçmişiyle veritabanını ayrıştırır ve uygulama bir sonraki açılışta şema doğrulamasında durur. Değişiklik yeni bir changeset ile gelir ([yeni API ucu ekleme](yeni-api-ucu-ekleme.md) 1. adım).

### 6. Dokümantasyon portalını başlatın (isteğe bağlı)

```bash
./docs/.tools/serve.sh            # http://localhost:8888
./docs/.tools/serve.sh --build    # yalnızca strict derleme
```

Betik ilk çalıştırmada `docs/.tools/.venv` altında bir Python sanal ortamı kurar ve MkDocs bağımlılıklarını indirir. Python 3.11+ gerekir. Bu portal yalnızca dokümantasyon içindir; uygulamanın bağımlılık bütçesine dahil değildir.

### 7. Uygulamayı çalıştırma

```bash
docker compose up -d --build --wait   # db + api (8080) + web (3000) + mailpit (8025)
```

- Vitrin: `http://localhost:3000`, e-postalar (mailpit): `http://localhost:8025`.
- `api` dev profiliyle açılır ve geliştirme verisini yükler (`contextFilter:dev`). Dev hesaplarının hepsinin parolası `gelistirme-parolasi-1`; bu parola yalnızca yerel veritabanında geçerlidir:

| Hesap | Ne için |
|---|---|
| `alici@ornek.test` | Olağan alıcı; e-postası doğrulanmamış |
| `iki-adim@ornek.test` | İkinci faktör açık |
| `admin@ornek.test` | `ROLE_ADMIN`: satıcı başvurularını karara bağlamak için (`/admin/sellers`) |
| `uretici@ornek.test` | E-postası doğrulanmış, satıcı kaydı yok: başvuru akışını denemek için |

- **Satıcı başvurusunu elle deneme:** `uretici@ornek.test` ile giriş yapın. **Hesabım → Satıcı olarak başvur** yolundan herhangi bir PDF, JPEG ya da PNG dosyası (en çok 10 MB) yükleyin. Sonra `admin@ornek.test` ile **Yönetim** ekranından karar verin. Seed başvuru içermez, çünkü belge dosyası Liquibase ile volume'a yazılamaz.
- **Yüklenen dosyalar** `api` konteynerinde `/app/uploads`'a bağlı `uploads` volume'unda durur (`BAHCEDEN_STORAGE_DIR`; [ADR-039](../adr/039-uploads-on-local-volume-served-by-api.md)). Konteyner yeniden kurulduğunda kaybolmazlar. Sıfırlamak için `docker compose down -v` hem veritabanını hem dosyaları siler.
- `api`'yi compose dışında (IDE'den) çalıştırıyorsanız `api/.env.example`'daki değişkenleri tanımlayın. `BAHCEDEN_STORAGE_DIR` yazılabilir bir dizini göstermeli; `./uploads` gitignore'dadır.

---

## Olası hatalar

- **Hata:** `java: command not found` veya yanlış sürüm (`java -version` 24 gösteriyor)
  **Çözüm:** Projenin SDK'sı IntelliJ'de `corretto-21.x` seçili mi kontrol edin (`Project Structure → Project → SDK`). Komut satırında çalışıyorsanız `JAVA_HOME` değişkenini o oturum için 21'e ayarlayın.

- **Hata:** `mvn: command not found`
  **Çözüm:** Maven'ı kurmak gerekmez; adım 3'teki bundled Maven yolunu kullanın veya `api/` içindeyken `./mvnw` çalıştırın.

- **Hata:** `failed to connect to the docker API at npipe:...`
  **Çözüm:** Docker Desktop çalışmıyor. Başlatın; `docker info` sunucu sürümü dönene kadar bekleyin.

- **Hata:** Testcontainers testleri `Could not find a valid Docker environment` ile düşüyor
  **Çözüm:** Docker çalışıyor olmalı ([ADR-005](../adr/005-test-strategy.md)); Windows'ta WSL2 arka ucunun açık olduğundan emin olun.

- **Hata:** `api` açılmıyor, log'da `Depolama dizini yazılabilir değil: bahceden.storage.dir`
  **Çözüm:** `BAHCEDEN_STORAGE_DIR` var olmayan ya da uygulama kullanıcısının yazamadığı bir dizini gösteriyor. Compose'da değer `/app/uploads`'tır ve imaj dizini hazırlar; IDE'den çalıştırıyorsanız dizini oluşturun.

- **Hata:** `pre-commit` hook'u hiç çalışmıyor
  **Çözüm:** `git config core.hooksPath .githooks` komutu klon başına bir kez çalıştırılmalıdır (adım 1).

---

## İlgili kararlar

- [ADR-001](../adr/001-spring-api-and-next-web.md) — iki dağıtılabilir birim (`api/` + `web/`)
- [ADR-002](../adr/002-dependency-budget.md) — bağımlılık bütçesi: neden ek araç kurulmuyor
- [ADR-016](../adr/016-liquibase-for-schema-migrations.md) — PostgreSQL ve Liquibase (ADR-004'ün Flyway kararını superseded eder)
- [ADR-005](../adr/005-test-strategy.md) — Testcontainers neden Docker gerektiriyor
