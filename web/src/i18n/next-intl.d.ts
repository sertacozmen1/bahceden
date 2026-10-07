import type { Locale } from "@/i18n/locale";
import type { Messages } from "@/i18n/messages";

/**
 * next-intl tip yapılandırması (ADR-029): `useTranslations` ve
 * `getTranslations` yalnızca `tr.json`'da var olan anahtarları kabul eder;
 * dil değerleri desteklenen dillerle sınırlıdır.
 */
declare module "next-intl" {
  interface AppConfig {
    Locale: Locale;
    Messages: Messages;
  }
}
