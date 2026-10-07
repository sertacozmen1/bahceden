import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

/**
 * Vitest yapılandırması (ADR-005).
 *
 * Davranış testi yapılır: rol ve erişilebilir ad ile sorgulanır, snapshot
 * alınmaz. Ağ her zaman taklit edilir; test gerçek servise çıkmaz (K10).
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
      "@contract": fileURLToPath(new URL("../shared/generated/api.ts", import.meta.url)),
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    include: ["src/**/*.test.{ts,tsx}"],
    // next-intl/server taklidi ve ortak test dili (ADR-029).
    setupFiles: ["./src/test/setup.ts"],
    css: true,
  },
});
