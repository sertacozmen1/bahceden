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
  /** JSON olarak gönderilecek gövde. */
  readonly body?: unknown;
  readonly accessToken?: string;
  /**
   * İstemcinin adres zinciri (`X-Forwarded-For`). `api` hız sınırını istemci
   * adresine göre uygular; BFF bu başlığı iletmezse tüm kullanıcılar web
   * sunucusunun adresini paylaşır (design.md D5).
   */
  readonly forwardedFor?: string | null;
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

  const headers: Record<string, string> = { Accept: "application/json" };
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

/** Durum değiştiren istek (POST, DELETE). Başarısız yanıt {@link ApiRequestError} fırlatır. */
export async function apiSend<T>(
  method: "POST" | "DELETE",
  path: string,
  options: SendOptions = {},
): Promise<SendResult<T>> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (options.accessToken !== undefined) {
    headers.Authorization = `Bearer ${options.accessToken}`;
  }
  if (options.forwardedFor) {
    headers["X-Forwarded-For"] = options.forwardedFor;
  }

  const response = await send(new URL(`${apiBaseUrl()}${path}`), {
    method,
    headers,
    cache: "no-store",
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (!response.ok) {
    throw await toApiError(response);
  }
  return { status: response.status, data: await readOptionalJson<T>(response) };
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
