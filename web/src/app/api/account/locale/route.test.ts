import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/apiError";

/**
 * Hesap dili eşitleme Route Handler'ı (add-account-locale-update D2). Oturum
 * yoksa ya da erişim token'ı geçmişse dil değişikliği hata sayılmaz. `api` ve
 * çerez katmanı taklit edilir; test hiçbir sunucuya çıkmaz (K10).
 */

const updateAccountLocale = vi.fn();
const readAccessToken = vi.fn();

vi.mock("@/features/account/api", () => ({ updateAccountLocale }));
vi.mock("@/lib/session", () => ({ readAccessToken }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { PUT } = await import("./route");

function aRequest(locale: string, site = "same-origin"): Request {
  return new Request("http://localhost:3000/api/account/locale", {
    method: "PUT",
    headers: { "Content-Type": "application/json", "sec-fetch-site": site },
    body: JSON.stringify({ locale }),
  });
}

beforeEach(() => {
  updateAccountLocale.mockReset();
  readAccessToken.mockReset();
  readAccessToken.mockResolvedValue("test-erisim");
});

describe("PUT /api/account/locale", () => {
  it("oturum varsa hesabın dilini sözleşmedeki değerle günceller", async () => {
    updateAccountLocale.mockResolvedValue(undefined);

    const response = await PUT(aRequest("en"));

    expect(response.status).toBe(204);
    expect(updateAccountLocale).toHaveBeenCalledWith("test-erisim", "EN");
  });

  it("oturum yoksa api'ye gitmez ve 204 döner", async () => {
    readAccessToken.mockResolvedValue(undefined);

    const response = await PUT(aRequest("en"));

    expect(response.status).toBe(204);
    expect(updateAccountLocale).not.toHaveBeenCalled();
  });

  it("erişim token'ı geçmişse (401) eşitlemeyi atlar ve 204 döner", async () => {
    updateAccountLocale.mockRejectedValue(
      new ApiRequestError(401, { code: "AUTH_REQUIRED", message: "Oturum gerekli", fieldErrors: [] }),
    );

    const response = await PUT(aRequest("tr"));

    expect(response.status).toBe(204);
    expect(updateAccountLocale).toHaveBeenCalledWith("test-erisim", "TR");
  });

  it("diğer api hatalarını aynen iletir", async () => {
    updateAccountLocale.mockRejectedValue(
      new ApiRequestError(400, { code: "VALIDATION_FAILED", message: "Geçersiz", fieldErrors: [] }),
    );

    const response = await PUT(aRequest("en"));

    expect(response.status).toBe(400);
    expect(await response.json()).toMatchObject({ code: "VALIDATION_FAILED" });
  });

  it("tanınmayan dili api'ye iletmeden 400 ile reddeder", async () => {
    const response = await PUT(aRequest("de"));

    expect(response.status).toBe(400);
    expect(await response.json()).toMatchObject({ code: "VALIDATION_FAILED" });
    expect(updateAccountLocale).not.toHaveBeenCalled();
  });

  it("başka siteden gelen isteği api'ye iletmeden 403 ile reddeder", async () => {
    const response = await PUT(aRequest("en", "cross-site"));

    expect(response.status).toBe(403);
    expect(updateAccountLocale).not.toHaveBeenCalled();
  });
});
