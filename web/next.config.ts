import type { NextConfig } from "next";

/**
 * Next.js yapılandırması.
 *
 * Sözleşme tipleri depo kökündeki shared/generated altından gelir ve yalnızca
 * `import type` ile kullanılır: derleme sırasında silinirler, bu yüzden paket
 * toplayıcı için dizin dışı bir çalışma zamanı bağımlılığı doğmaz (ADR-003).
 *
 * Çeviri altyapısı (ADR-029): next-intl, istek başına dil ve mesajları
 * `next-intl/config` modülünden okur; bu takma ad `src/i18n/request.ts`'e
 * bağlanır. next-intl'in `createNextIntlPlugin` sarmalayıcısı bilinçli olarak
 * KULLANILMAZ: biz deneysel mesaj çıkarma özelliğini kullanmadığımızda bile
 * yapılandırma yüklenirken `@swc/core` ve `@parcel/watcher` yerel ikililerini
 * yüklüyor; bu, derlemeyi makinenin yerel ikili önbelleğine bağımlı kılıyor.
 * Sarmalayıcının bu kurulumda yaptığı tek iş aşağıdaki takma addır
 * (`trailingSlash` ve `basePath` kullanılmıyor). Next 16'da hem `dev` hem
 * `build` Turbopack ile çalışır; takma ad göreli yolla verilir (Turbopack
 * mutlak yol kabul etmez).
 */
const nextConfig: NextConfig = {
  reactStrictMode: true,
  typedRoutes: true,
  // Konteyner imajı için: yalnızca gereken dosyalar ve bağımlılıklar kopyalanır.
  output: "standalone",
  turbopack: {
    resolveAlias: {
      "next-intl/config": "./src/i18n/request.ts",
    },
  },
};

export default nextConfig;
