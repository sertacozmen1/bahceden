import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/apiError";

/**
 * İki adımlı giriş Route Handler'ı (add-two-factor-toggle D2). Siteler arası
 * istek `api`'ye ulaşmaz; çerezlere dokunulmaz. `api` ve çerez katmanı taklit
 * edilir; test hiçbir sunucuya çıkmaz (K10).
 */

const changeTwoFactor = vi.fn();
const clearSession = vi.fn();

vi.mock("@/features/account/api", () => ({ changeTwoFactor }));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim", clearSession }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { PUT } = await import("./route");

function aRequest(body: unknown, site = "same-origin"): Request {
  return new Request("http://localhost:3000/api/account/two-factor", {
    method: "PUT",
    headers: { "Content-Type": "application/json", "sec-fetch-site": site },
    body: JSON.stringify(body),
  });
}

beforeEach(() => {
  changeTwoFactor.mockReset();
  clearSession.mockReset();
});

describe("PUT /api/account/two-factor", () => {
  it("api'ye iletir, 204 döner ve çerezlere dokunmaz", async () => {
    changeTwoFactor.mockResolvedValue(undefined);

    const response = await PUT(aRequest({ enabled: true, currentPassword: "cok-gizli-parola" }));

    expect(response.status).toBe(204);
    expect(changeTwoFactor).toHaveBeenCalledWith("test-erisim", true, "cok-gizli-parola");
    expect(clearSession).not.toHaveBeenCalled();
  });

  it("api hatasını aynen iletir", async () => {
    changeTwoFactor.mockRejectedValue(
      new ApiRequestError(422, {
        code: "TWO_FACTOR_EMAIL_NOT_VERIFIED",
        message: "Önce e-posta adresinizi doğrulayın",
        fieldErrors: [],
      }),
    );

    const response = await PUT(aRequest({ enabled: true, currentPassword: "cok-gizli-parola" }));

    expect(response.status).toBe(422);
    expect(await response.json()).toMatchObject({ code: "TWO_FACTOR_EMAIL_NOT_VERIFIED" });
  });

  it("açık/kapalı değeri mantıksal değilse api'ye gitmeden 400", async () => {
    const response = await PUT(aRequest({ enabled: "evet", currentPassword: "cok-gizli-parola" }));

    expect(response.status).toBe(400);
    expect(changeTwoFactor).not.toHaveBeenCalled();
  });

  it("başka siteden gelen isteği api'ye iletmeden 403 ile reddeder", async () => {
    const response = await PUT(aRequest({ enabled: false, currentPassword: "x" }, "cross-site"));

    expect(response.status).toBe(403);
    expect(changeTwoFactor).not.toHaveBeenCalled();
  });
});
