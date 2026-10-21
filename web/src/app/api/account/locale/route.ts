import { type AccountLocale, updateAccountLocale } from "@/features/account/api";
import { handleBffMutation, invalidRequestResponse, stringField } from "@/features/account/routeSupport";
import { type Locale, parseLocale } from "@/i18n/locale";
import { ApiRequestError } from "@/lib/apiError";
import { readAccessToken } from "@/lib/session";

/**
 * Dil menüsünün seçimini hesaba yazar (BFF, add-account-locale-update D2).
 *
 * Dil çerezi istemcide zaten yazılmıştır; bu uç yalnızca hesabın e-posta
 * dilini eşitler ve en iyi çabadır:
 * - oturum çerezi yoksa `api`'ye gidilmez;
 * - erişim token'ı geçmişse (`401`) eşitleme atlanır, hesabın dili bir sonraki
 *   girişte yetişir (ADR-032).
 * İki durumda da `204` döner: ekrandaki dil değişikliği bir hata değildir.
 */

const HTTP_NO_CONTENT = 204;
const HTTP_UNAUTHORIZED = 401;

/** Arayüz dili → sözleşmedeki hesap dili. */
const ACCOUNT_LOCALES = { tr: "TR", en: "EN" } as const satisfies Record<Locale, AccountLocale>;

export async function PUT(request: Request): Promise<Response> {
  return handleBffMutation(request, async (body) => {
    const locale = parseLocale(stringField(body, "locale"));
    if (locale === undefined) {
      return invalidRequestResponse();
    }
    const accessToken = await readAccessToken();
    if (accessToken === undefined) {
      return new Response(null, { status: HTTP_NO_CONTENT });
    }
    try {
      await updateAccountLocale(accessToken, ACCOUNT_LOCALES[locale]);
    } catch (error) {
      if (!(error instanceof ApiRequestError && error.status === HTTP_UNAUTHORIZED)) {
        throw error;
      }
    }
    return new Response(null, { status: HTTP_NO_CONTENT });
  });
}
