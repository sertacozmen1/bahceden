import type { NextConfig } from "next";

/**
 * Next.js yapılandırması.
 *
 * Sözleşme tipleri depo kökündeki shared/generated altından gelir ve yalnızca
 * `import type` ile kullanılır: derleme sırasında silinirler, bu yüzden paket
 * toplayıcı için dizin dışı bir çalışma zamanı bağımlılığı doğmaz (ADR-003).
 */
const nextConfig: NextConfig = {
  reactStrictMode: true,
  typedRoutes: true,
  // Konteyner imajı için: yalnızca gereken dosyalar ve bağımlılıklar kopyalanır.
  output: "standalone",
};

export default nextConfig;
