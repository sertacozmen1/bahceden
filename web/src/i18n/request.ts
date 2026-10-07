import { cookies, headers } from "next/headers";
import { getRequestConfig } from "next-intl/server";
import { LOCALE_COOKIE, resolveLocale } from "@/i18n/locale";
import { messagesFor } from "@/i18n/messages";

/**
 * next-intl istek yapılandırması (ADR-029): istek başına dil ve mesajlar.
 *
 * Yönlendirmesiz kurulum: dil URL'den değil çerezden ve tarayıcı başlığından
 * seçilir (ADR-030). Sunucu bileşenleri, Route Handler'lar ve kök düzendeki
 * istemci sağlayıcısı aynı sonucu kullanır.
 */
export default getRequestConfig(async () => {
  const locale = resolveLocale((await cookies()).get(LOCALE_COOKIE)?.value, (await headers()).get("accept-language"));
  return { locale, messages: messagesFor(locale) };
});
