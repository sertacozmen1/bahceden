import coreWebVitals from "eslint-config-next/core-web-vitals";
import nextTypescript from "eslint-config-next/typescript";

/**
 * ESLint yapılandırması (flat config).
 *
 * eslint-config-next 16 doğrudan flat config dizisi ihraç ediyor; FlatCompat
 * sarmalayıcısına gerek yok (ve sarmalanınca kırılıyor).
 *
 * Kurallar Next'in önerdiği setten gelir; ek kural eklenmedi: lint bir stil
 * tartışması aracı değil, hata yakalama aracıdır.
 */
const config = [
  { ignores: [".next/**", "node_modules/**", "next-env.d.ts"] },
  ...coreWebVitals,
  ...nextTypescript,
];

export default config;
