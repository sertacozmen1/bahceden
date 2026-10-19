import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/apiError";

/**
 * Parola değiştirme Route Handler'ı (ADR-023, add-password-change D4). Siteler
 * arası istek `api`'ye ulaşmaz; çerezler yalnızca başarıda silinir. `api` ve
 * çerez katmanı taklit edilir; test hiçbir sunucuya çıkmaz (K10).
 */

const changePassword = vi.fn();
const clearSession = vi.fn();

vi.mock("@/features/account/api", () => ({ changePassword }));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim", clearSession }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { POST } = await import("./route");

const body = { currentPassword: "eski-gizli-parola", newPassword: "yepyeni-gizli-parola" };

function aRequest(site: string): Request {
  return new Request("http://localhost:3000/api/auth/password-change", {
    method: "POST",
    headers: { "Content-Type": "application/json", "sec-fetch-site": site },
    body: JSON.stringify(body),
  });
}

beforeEach(() => {
  changePassword.mockReset();
  clearSession.mockReset();
});

describe("POST /api/auth/password-change", () => {
  it("başka siteden gelen isteği api'ye iletmeden 403 ile reddeder", async () => {
    const response = await POST(aRequest("cross-site"));

    expect(response.status).toBe(403);
    expect(changePassword).not.toHaveBeenCalled();
    expect(clearSession).not.toHaveBeenCalled();
  });

  it("başarıda oturum çerezlerini siler ve 204 döner", async () => {
    changePassword.mockResolvedValue(undefined);

    const response = await POST(aRequest("same-origin"));

    expect(response.status).toBe(204);
    expect(changePassword).toHaveBeenCalledWith("test-erisim", "eski-gizli-parola", "yepyeni-gizli-parola");
    expect(clearSession).toHaveBeenCalledOnce();
  });

  it("api hatasını aynen iletir ve çerezlere dokunmaz", async () => {
    changePassword.mockRejectedValue(
      new ApiRequestError(400, {
        code: "VALIDATION_FAILED",
        message: "Gönderilen bilgiler geçersiz",
        fieldErrors: [{ field: "currentPassword", message: "Mevcut parola doğru değil" }],
      }),
    );

    const response = await POST(aRequest("same-origin"));

    expect(response.status).toBe(400);
    expect(await response.json()).toMatchObject({
      code: "VALIDATION_FAILED",
      fieldErrors: [{ field: "currentPassword", message: "Mevcut parola doğru değil" }],
    });
    expect(clearSession).not.toHaveBeenCalled();
  });
});
