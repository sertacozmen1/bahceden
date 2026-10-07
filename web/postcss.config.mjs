/**
 * PostCSS yapılandırması: yalnızca Tailwind v4 eklentisi (ADR-027).
 *
 * Tailwind v4 kendi içe aktarma ve önek işlemesini yapar; ayrı autoprefixer
 * veya postcss-import gerekmez.
 */
const config = {
  plugins: {
    "@tailwindcss/postcss": {},
  },
};

export default config;
