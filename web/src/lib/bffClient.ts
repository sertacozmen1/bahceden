import { ApiUnavailableError, toApiError } from "@/lib/apiError";

/**
 * Tarayıcıdan kendi Route Handler'larımıza (BFF) konuşan tek katman.
 *
 * `apiClient` ile ayrı olmasının sebebi hedeftir: `apiClient` sunucu tarafında
 * `api`'ye konuşur ve ortam değişkeni okur; tarayıcı `api`'ye hiç konuşmaz
 * (design.md D6). Hata gövdesi çözümü ortaktır (`apiError`), ikinci kez
 * yazılmaz (AGENTS.md K9).
 *
 * Çerezler aynı kökene otomatik gider; token bu katmanda görünmez.
 */
export async function postToBff<T>(path: string, body?: unknown): Promise<T | undefined> {
  let response: Response;
  try {
    response = await fetch(path, {
      method: "POST",
      headers: body === undefined ? { Accept: "application/json" } : {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (cause) {
    throw new ApiUnavailableError("Sunucuya ulaşılamadı.", { cause });
  }

  if (!response.ok) {
    throw await toApiError(response);
  }
  // Gövdesiz başarı (204, gövdesiz 202) "gövde yok" demektir.
  try {
    const text = await response.text();
    return text.trim() === "" ? undefined : (JSON.parse(text) as T);
  } catch (cause) {
    throw new ApiUnavailableError("Sunucu yanıtı okunamadı.", { cause });
  }
}
