# Bahçeden API imajı: çok aşamalı derleme.
#
# Derleme ve çalışma zamanı ayrıştırıldı: üretim imajında Maven, kaynak kod ve
# derleme önbelleği bulunmaz. Temel imajlar ADR-002 bütçesindeki Java 21 (LTS)
# sürümüne sabitlidir.

# ── Aşama 1: derleme ────────────────────────────────────────
FROM amazoncorretto:21-alpine AS build
WORKDIR /build

# Önce yalnızca bağımlılık tanımları kopyalanır: kaynak değiştiğinde
# bağımlılıklar yeniden indirilmez (katman önbelleği).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Testler CI'da koşar; imaj derlemesi test koşmaz (ADR-005).
RUN ./mvnw -B -q clean package -DskipTests

# ── Aşama 2: çalışma zamanı ─────────────────────────────────
FROM amazoncorretto:21-alpine
WORKDIR /app

# Uygulama root olmayan kullanıcıyla çalışır.
RUN addgroup -S bahceden && adduser -S -G bahceden bahceden
# Yüklenen dosyaların dizini (ADR-039). Boş bir adlandırılmış volume ilk
# bağlandığında imajdaki dizinin sahipliğini devralır; uygulama kullanıcısı
# yazabilsin diye dizin burada açılır.
RUN mkdir -p /app/uploads && chown bahceden:bahceden /app/uploads
COPY --from=build /build/target/*.jar app.jar
USER bahceden

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
