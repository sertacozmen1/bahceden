import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError, ApiUnavailableError, apiSend } from "./apiClient";

/**
 * Tek HTTP katmanının gönderim davranışı (AGENTS.md §12.3). Ağ taklit edilir.
 */

beforeEach(() => {
  vi.stubEnv("BAHCEDEN_API_BASE_URL", "http://api.ornek.test");
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("apiSend", () => {
  it("gövdesiz 202 yanıtı hata saymaz", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 202 })));

    await expect(apiSend("POST", "/api/v1/accounts/me/verification-requests")).resolves.toEqual({
      status: 202,
      data: undefined,
    });
  });

  it("erişim token'ını ve istemci adres zincirini başlıkla iletir", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await apiSend("POST", "/api/v1/x", { accessToken: "test-erisim", forwardedFor: "203.0.113.7", body: {} });

    const init: RequestInit | undefined = fetchMock.mock.calls[0]?.[1];
    expect(init?.headers).toMatchObject({
      Authorization: "Bearer test-erisim",
      "X-Forwarded-For": "203.0.113.7",
      "Content-Type": "application/json",
    });
  });

  it("sözleşmeye uygun hata gövdesini ApiRequestError olarak fırlatır", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: "RATE_LIMITED", message: "Çok fazla deneme.", fieldErrors: [] }), {
          status: 429,
        }),
      ),
    );

    const error = await apiSend("POST", "/api/v1/sessions").catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiRequestError);
    expect(error).toMatchObject({ status: 429, code: "RATE_LIMITED" });
  });

  it("sözleşme dışı hata gövdesinde ayrıntı uydurmaz", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("<html>", { status: 502 })));

    await expect(apiSend("POST", "/api/v1/sessions")).rejects.toBeInstanceOf(ApiUnavailableError);
  });
});
