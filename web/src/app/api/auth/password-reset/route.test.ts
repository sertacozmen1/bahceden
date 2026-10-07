import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Yeni parola Route Handler'ı (ADR-023, ADR-035). Hesap devralma yüzeyi
 * olduğu için siteler arası istek `api`'ye hiç ulaşmadan reddedilmeli.
 * `api` çağrısı taklit edilir; test hiçbir sunucuya çıkmaz (K10).
 */

const resetPassword = vi.fn();

vi.mock("@/features/account/api", () => ({ resetPassword }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { POST } = await import("./route");

function aRequest(site: string, body: unknown): Request {
  return new Request("http://localhost:3000/api/auth/password-reset", {
    method: "POST",
    headers: { "Content-Type": "application/json", "sec-fetch-site": site },
    body: JSON.stringify(body),
  });
}

beforeEach(() => {
  resetPassword.mockReset();
});

describe("POST /api/auth/password-reset", () => {
  it("başka siteden gelen isteği api'ye iletmeden 403 ile reddeder", async () => {
    const response = await POST(aRequest("cross-site", { token: "ornek-token", password: "yepyeni-gizli-parola" }));

    expect(response.status).toBe(403);
    expect(resetPassword).not.toHaveBeenCalled();
  });

  it("aynı kökenden gelen isteği iletir ve oturum çerezi yazmadan 204 döner", async () => {
    resetPassword.mockResolvedValue(undefined);

    const response = await POST(aRequest("same-origin", { token: "ornek-token", password: "yepyeni-gizli-parola" }));

    expect(response.status).toBe(204);
    expect(response.headers.get("set-cookie")).toBeNull();
    expect(resetPassword).toHaveBeenCalledWith("ornek-token", "yepyeni-gizli-parola", null);
  });
});
