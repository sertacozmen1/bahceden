import { getLocale } from "next-intl/server";
import { ApiUnavailableError, toApiError } from "@/lib/apiError";
import { apiBaseUrl } from "@/lib/env";

/**
 * `api` ile konuşan TEK HTTP katmanı (AGENTS.md §12.3).
 *
 * İkinci bir `fetch` sarmalayıcı yazılmaz: temel adres, kimlik başlığı ve hata
 * gövdesi çözümü tek yerde durur. Hata gövdesi sözleşmedeki `ApiError`
 * şeklidir; her çağıran bunu kendi başına ayrıştırmaz.
 *
 * Yalnızca sunucu tarafında çalışır: tarayıcı `api`'ye hiç konuşmaz, kendi
 * Route Handler'larımıza konuşur (design.md D6). Token tarayıcıya verilmez.
 *
 * Tipler sözleşmeden üretilir ve elle yazılmaz (ADR-003, ADR-009).
 */

export { ApiRequestError, ApiUnavailableError } from "@/lib/apiError";

type RequestOptions = {
  /** Sorgu parametreleri; tanımsız değerler atlanır. */
  readonly query?: Record<string, string | number | undefined>;
  /**
   * Next.js önbellek ömrü (saniye). Vitrin verisi kısa süre önbelleklenebilir;
   * oturuma bağlı veri için bu değer verilmez.
   */
  readonly revalidateSeconds?: number;
  /** Erişim token'ı: `Authorization: Bearer` olarak gönderilir; oturumlu istek önbelleklenmez. */
  readonly accessToken?: string;
};

type SendOptions = {
  /**
   * Gövde. `FormData` ise `multipart/form-data` olarak olduğu gibi iletilir
   * (sınır değerini `fetch` kendisi yazar); değilse JSON'a çevrilir.
   */
  readonly body?: unknown;
  readonly accessToken?: string;
  /**
   * İstemcinin adres zinciri (`X-Forwarded-For`). `api` hız sınırını istemci
   * adresine göre uygular; BFF bu başlığı iletmezse tüm kullanıcılar web
   * sunucusunun adresini paylaşır (design.md D5).
   */
  readonly forwardedFor?: string | null;
  /**
   * Tekrar anahtarı (`Idempotency-Key`): aynı anahtarla tekrarlanan istek
   * ikinci kez işlenmez (AGENTS.md §11.5, add-cart-and-checkout D6).
   */
  readonly idempotencyKey?: string;
};

/** Gönderimin sonucu: başarılı durum kodu ve varsa gövde (204'te yok). */
export type SendResult<T> = { readonly status: number; readonly data: T | undefined };

export async function apiGet<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const url = new URL(`${apiBaseUrl()}${path}`);
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== undefined) {
      url.searchParams.set(key, String(value));
    }
  }

  const headers = await baseHeaders();
  if (options.accessToken !== undefined) {
    headers.Authorization = `Bearer ${options.accessToken}`;
  }

  const response = await send(url, {
    headers,
    ...(options.accessToken !== undefined
      ? { cache: "no-store" }
      : options.revalidateSeconds === undefined
        ? {}
        : { next: { revalidate: options.revalidateSeconds } }),
  });

  if (!response.ok) {
    throw await toApiError(response);
  }
  return readJson<T>(response);
}

/** Durum değiştiren istek (POST, PUT, DELETE). Başarısız yanıt {@link ApiRequestError} fırlatır. */
export async function apiSend<T>(
  method: "POST" | "PUT" | "DELETE",
  path: string,
  options: SendOptions = {},
): Promise<SendResult<T>> {
  const headers = await baseHeaders();
  const isForm = options.body instanceof FormData;
  if (options.body !== undefined && !isForm) {
    headers["Content-Type"] = "application/json";
  }
  if (options.accessToken !== undefined) {
    headers.Authorization = `Bearer ${options.accessToken}`;
  }
  if (options.forwardedFor) {
    headers["X-Forwarded-For"] = options.forwardedFor;
  }
  if (options.idempotencyKey !== undefined) {
    headers["Idempotency-Key"] = options.idempotencyKey;
  }

  const response = await send(new URL(`${apiBaseUrl()}${path}`), {
    method,
    headers,
    cache: "no-store",
    body: requestBody(options.body),
  });

  if (!response.ok) {
    throw await toApiError(response);
  }
  return { status: response.status, data: await readOptionalJson<T>(response) };
}

/**
 * İkili içerik (belge) için ham yanıt: gövde okunmadan döner, çağıran onu
 * tarayıcıya akış olarak iletir. Başarısız yanıt yine ortak hata gövdesiyle
 * {@link ApiRequestError} fırlatır.
 */
export async function apiDownload(path: string, accessToken: string | undefined): Promise<Response> {
  const headers = await baseHeaders();
  headers.Accept = "*/*";
  if (accessToken !== undefined) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  const response = await send(new URL(`${apiBaseUrl()}${path}`), { headers, cache: "no-store" });
  if (!response.ok) {
    throw await toApiError(response);
  }
  return response;
}

function requestBody(body: unknown): BodyInit | undefined {
  if (body === undefined) {
    return undefined;
  }
  return body instanceof FormData ? body : JSON.stringify(body);
}

/**
 * Her isteğin ortak başlıkları. Sayfanın seçili dili `Accept-Language` olarak
 * iletilir (ADR-030): `api` tarayıcının başlığını değil `web`'in seçtiği dili
 * görür, hata ve bilgi mesajları ekranla aynı dilde gelir.
 */
async function baseHeaders(): Promise<Record<string, string>> {
  return { Accept: "application/json", "Accept-Language": await getLocale() };
}

async function send(url: URL, init: RequestInit): Promise<Response> {
  try {
    return await fetch(url, init);
  } catch (cause) {
    throw new ApiUnavailableError("Sunucuya ulaşılamadı.", { cause });
  }
}

/**
 * Gövdesi boş olabilen yanıt: 204 de, gövdesiz 202 de (ör. yeni doğrulama
 * bağlantısı) "gövde yok" demektir. Boş gövde JSON olarak okunmaya çalışılmaz.
 */
async function readOptionalJson<T>(response: Response): Promise<T | undefined> {
  let text: string;
  try {
    text = await response.text();
  } catch (cause) {
    throw new ApiUnavailableError("Sunucu yanıtı okunamadı.", { cause });
  }
  if (text.trim() === "") {
    return undefined;
  }
  try {
    return JSON.parse(text) as T;
  } catch (cause) {
    throw new ApiUnavailableError("Sunucu yanıtı okunamadı.", { cause });
  }
}

async function readJson<T>(response: Response): Promise<T> {
  try {
    return (await response.json()) as T;
  } catch (cause) {
    throw new ApiUnavailableError("Sunucu yanıtı okunamadı.", { cause });
  }
}
