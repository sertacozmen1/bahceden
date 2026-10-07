import { afterEach, vi } from "vitest";
import { testLocale } from "@/test/intl";

/**
 * Vitest ortak kurulumu.
 *
 * `next-intl/server` istek bağlamı ister (çerez, başlık); test ortamında istek
 * yoktur. Burada aynı API gerçek mesaj dosyalarıyla ve `testLocale` diliyle
 * taklit edilir: sunucu bileşenleri ve `apiClient` gerçek çeviriyle çalışır,
 * ağa ve sunucuya çıkılmaz (AGENTS.md §12.6).
 */
vi.mock("next-intl/server", async () => {
  const { createTranslator } = await import("next-intl");
  const { messagesFor } = await import("@/i18n/messages");
  return {
    getLocale: async () => testLocale.current,
    getTranslations: async () =>
      createTranslator({ locale: testLocale.current, messages: messagesFor(testLocale.current) }),
  };
});

afterEach(() => {
  testLocale.current = "tr";
});
