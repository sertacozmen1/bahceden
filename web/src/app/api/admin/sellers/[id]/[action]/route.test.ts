import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Admin kararı Route Handler'ı (ADR-023, add-seller-onboarding D7). Eylem
 * beyaz listede değilse veya kimlik sayı değilse `api`'ye hiç gidilmez;
 * siteler arası istek reddedilir. `api` çağrısı taklit edilir (K10).
 */

const decideSeller = vi.fn();

vi.mock("@/features/admin/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/admin/api")>()),
  decideSeller,
}));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim" }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { POST } = await import("./route");

function aRequest(site: string, body: unknown): Request {
  return new Request("http://localhost:3000/api/admin/sellers/7/reject", {
    method: "POST",
    headers: { "Content-Type": "application/json", "sec-fetch-site": site },
    body: JSON.stringify(body),
  });
}

function contextFor(id: string, action: string) {
  return { params: Promise.resolve({ id, action }) };
}

beforeEach(() => {
  decideSeller.mockReset();
});

describe("POST /api/admin/sellers/[id]/[action]", () => {
  it("bilinmeyen eylemi ve sayı olmayan kimliği api'ye iletmeden 404 ile reddeder", async () => {
    expect((await POST(aRequest("same-origin", {}), contextFor("7", "delete"))).status).toBe(404);
    expect((await POST(aRequest("same-origin", {}), contextFor("abc", "approve"))).status).toBe(404);
    expect(decideSeller).not.toHaveBeenCalled();
  });

  it("başka siteden gelen isteği 403 ile reddeder", async () => {
    const response = await POST(aRequest("cross-site", { reason: "x" }), contextFor("7", "reject"));

    expect(response.status).toBe(403);
    expect(decideSeller).not.toHaveBeenCalled();
  });

  it("geçerli kararı gerekçesiyle iletir", async () => {
    decideSeller.mockResolvedValue({ id: 7, status: "REJECTED" });

    const response = await POST(aRequest("same-origin", { reason: "Belge okunmuyor, yeniden yükleyin." }),
      contextFor("7", "reject"));

    expect(response.status).toBe(200);
    expect(decideSeller).toHaveBeenCalledWith("test-erisim", 7, "reject", "Belge okunmuyor, yeniden yükleyin.");
  });
});
