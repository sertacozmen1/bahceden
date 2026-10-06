import type { components } from "@contract";
import { apiBaseUrl } from "@/lib/env";

/**
 * `api` ile konuşan TEK HTTP katmanı (AGENTS.md §12.3).
 *
 * İkinci bir `fetch` sarmalayıcı yazılmaz: temel adres, hata gövdesi çözümü ve
 * ileride kimlik başlığı tek yerde durur. Hata gövdesi sözleşmedeki `ApiError`
 * şeklidir; her çağıran bunu kendi başına ayrıştırmaz.
 *
 * Tipler sözleşmeden üretilir ve elle yazılmaz (ADR-003, ADR-009).
 */

type ApiError = components["schemas"]["ApiError"];

/** `api`'den gelen, sözleşmeye uygun hata. */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: ReadonlyArray<{ field: string; message: string }>;

  constructor(status: number, body: ApiError) {
    super(body.message);
    this.name = "ApiRequestError";
    this.status = status;
    this.code = body.code;
    this.fieldErrors = body.fieldErrors;
  }
}

/** Ağ veya ayrıştırma düzeyinde başarısızlık: sunucu sözleşmeye uygun yanıt vermedi. */
export class ApiUnavailableError extends Error {
  constructor(message: string, options?: { cause?: unknown }) {
    super(message, options);
    this.name = "ApiUnavailableError";
  }
}

type RequestOptions = {
  /** Sorgu parametreleri; tanımsız değerler atlanır. */
  readonly query?: Record<string, string | number | undefined>;
  /**
   * Next.js önbellek ömrü (saniye). Vitrin verisi kısa süre önbelleklenebilir;
   * oturuma bağlı veri için bu değer verilmez.
   */
  readonly revalidateSeconds?: number;
};

export async function apiGet<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const url = new URL(`${apiBaseUrl()}${path}`);
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== undefined) {
      url.searchParams.set(key, String(value));
    }
  }

  let response: Response;
  try {
    response = await fetch(url, {
      headers: { Accept: "application/json" },
      next:
        options.revalidateSeconds === undefined
          ? undefined
          : { revalidate: options.revalidateSeconds },
    });
  } catch (cause) {
    throw new ApiUnavailableError("Sunucuya ulaşılamadı.", { cause });
  }

  if (!response.ok) {
    throw await toError(response);
  }

  try {
    return (await response.json()) as T;
  } catch (cause) {
    throw new ApiUnavailableError("Sunucu yanıtı okunamadı.", { cause });
  }
}

async function toError(response: Response): Promise<Error> {
  try {
    const body = (await response.json()) as ApiError;
    return new ApiRequestError(response.status, body);
  } catch {
    // Sözleşme dışı bir hata gövdesi: ayrıntıyı uydurmak yerine durumu bildir.
    return new ApiUnavailableError(`Sunucu beklenmeyen bir yanıt verdi (${response.status}).`);
  }
}
