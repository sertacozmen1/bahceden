import type { components } from "@contract";

/**
 * Sözleşmedeki ortak hata gövdesi ve onu taşıyan hata tipleri (AGENTS.md §11.5).
 *
 * İki katman aynı gövdeyi okur: sunucu tarafındaki `apiClient` (`api`'ye
 * konuşur) ve tarayıcıdaki `bffClient` (kendi Route Handler'larımıza konuşur;
 * onlar `api` hatasını aynen iletir). Çözüm tek yerde durur; ortam bağımlılığı
 * olmadığı için iki taraf da bu modülü içe aktarabilir.
 */

export type ApiErrorBody = components["schemas"]["ApiError"];

/** Sözleşmeye uygun hata yanıtı. */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: ReadonlyArray<{ field: string; message: string }>;

  constructor(status: number, body: ApiErrorBody) {
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

/** Başarısız bir yanıtı hata tipine çevirir; sözleşme dışı gövdede ayrıntı uydurmaz. */
export async function toApiError(response: Response): Promise<Error> {
  try {
    const body: unknown = await response.json();
    if (isApiErrorBody(body)) {
      return new ApiRequestError(response.status, body);
    }
  } catch {
    // Gövde JSON değil: aşağıdaki genel hata döner.
  }
  return new ApiUnavailableError(`Sunucu beklenmeyen bir yanıt verdi (${response.status}).`);
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  return (
    "code" in value &&
    typeof value.code === "string" &&
    "message" in value &&
    typeof value.message === "string" &&
    "fieldErrors" in value &&
    Array.isArray(value.fieldErrors)
  );
}
