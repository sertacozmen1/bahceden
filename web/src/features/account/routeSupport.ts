import { getTranslations } from "next-intl/server";
import { ApiRequestError } from "@/lib/apiError";
import { isSameOriginRequest } from "@/lib/sameOrigin";
import { bffErrorCodes } from "./bff";

/**
 * Hesap Route Handler'larının ortak adımları: köken denetimi, gövde okuma ve
 * hatayı tarayıcıya ortak gövdeyle iletme (design.md D6, AGENTS.md §11.5).
 */

const HTTP_BAD_REQUEST = 400;
const HTTP_FORBIDDEN = 403;
const HTTP_BAD_GATEWAY = 502;

/**
 * Durum değiştiren bir BFF isteğini çalıştırır. Siteler arası istek `api`'ye
 * hiç gitmeden reddedilir; `api` hatası aynen, ulaşılamazsa genel bir hata döner.
 */
export async function handleBffMutation(
  request: Request,
  action: (body: Record<string, unknown>) => Promise<Response>,
): Promise<Response> {
  return runBffAction(request, async () => {
    const body = await readJsonObject(request);
    return body === undefined ? INVALID_BODY : action(body);
  });
}

/**
 * Dosya yüklemeli (`multipart/form-data`) BFF isteği: aynı köken denetimi ve
 * hata iletimi, gövde JSON yerine `FormData` olarak okunur ve `api`'ye olduğu
 * gibi iletilir (add-seller-onboarding D3).
 */
export async function handleBffFormMutation(
  request: Request,
  action: (form: FormData) => Promise<Response>,
): Promise<Response> {
  return runBffAction(request, async () => {
    let form: FormData;
    try {
      form = await request.formData();
    } catch {
      return INVALID_BODY;
    }
    return action(form);
  });
}

/** Adımın "gövde okunamadı" sonucu: metni dil bilen ortak adım üretir. */
const INVALID_BODY = Symbol("invalid-body");

async function runBffAction(
  request: Request,
  step: () => Promise<Response | typeof INVALID_BODY>,
): Promise<Response> {
  // BFF'in kendi ürettiği hatalar da sayfanın dilinde (ADR-030); `api` hataları zaten o dilde gelir.
  const t = await getTranslations();
  if (!isSameOriginRequest(request)) {
    return errorResponse(HTTP_FORBIDDEN, bffErrorCodes.crossSite, t("account.errors.crossSite"));
  }

  try {
    const result = await step();
    return result === INVALID_BODY
      ? errorResponse(HTTP_BAD_REQUEST, bffErrorCodes.invalidBody, t("account.errors.invalidRequest"))
      : result;
  } catch (error) {
    if (error instanceof ApiRequestError) {
      return apiErrorResponse(error);
    }
    // Ayrıntı yalnızca sunucu tarafında; tarayıcıya genel mesaj (K10).
    return errorResponse(HTTP_BAD_GATEWAY, bffErrorCodes.unavailable, t("account.errors.unavailable"));
  }
}

/** `api` hatasını tarayıcıya aynı ortak gövdeyle ve aynı durum koduyla iletir. */
export function apiErrorResponse(error: ApiRequestError): Response {
  return Response.json(
    { code: error.code, message: error.message, fieldErrors: error.fieldErrors },
    { status: error.status },
  );
}

/** Gövdedeki bir alanı metin olarak okur; yoksa boş metin (doğrulama otoritesi `api`). */
export function stringField(body: Record<string, unknown>, name: string): string {
  const value = body[name];
  return typeof value === "string" ? value : "";
}

/** İstemcinin adres zinciri: `api` hız sınırı için iletilir (design.md D5). */
export function forwardedFor(request: Request): string | null {
  return request.headers.get("x-forwarded-for");
}

async function readJsonObject(request: Request): Promise<Record<string, unknown> | undefined> {
  if (request.headers.get("content-length") === "0") {
    return {};
  }
  try {
    const parsed: unknown = await request.json();
    if (typeof parsed === "object" && parsed !== null && !Array.isArray(parsed)) {
      return Object.fromEntries(Object.entries(parsed));
    }
    return undefined;
  } catch {
    // Gövdesiz istek (çıkış, yeni bağlantı) geçerlidir.
    return {};
  }
}

function errorResponse(status: number, code: string, message: string): Response {
  return Response.json({ code, message, fieldErrors: [] }, { status });
}
