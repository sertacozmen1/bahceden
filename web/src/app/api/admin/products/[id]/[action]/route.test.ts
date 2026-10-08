import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Admin ilan denetimi Route Handler'ı (ADR-023, add-product-listings D7).
 * Bilinmeyen eylem ve sayı olmayan kimlik `api`'ye gitmez; siteler arası istek
 * reddedilir. `api` taklit edilir (K10).
 */

const decideProduct = vi.fn();

vi.mock("@/features/admin/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/admin/api")>()),
  decideProduct,
}));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim" }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { POST } = await import("./route");

function aRequest(site: string, body: unknown): Request {
  return new Request("http://localhost:3000/api/admin/products/9/remove", {
    method: "POST",
    headers: { "Content-Type": "application/json", "sec-fetch-site": site },
    body: JSON.stringify(body),
  });
}

const contextFor = (id: string, action: string) => ({ params: Promise.resolve({ id, action }) });

beforeEach(() => {
  decideProduct.mockReset();
});

describe("POST /api/admin/products/[id]/[action]", () => {
  it("bilinmeyen eylemi ve sayı olmayan kimliği api'ye iletmeden 404 ile reddeder", async () => {
    expect((await POST(aRequest("same-origin", {}), contextFor("9", "delete"))).status).toBe(404);
    expect((await POST(aRequest("same-origin", {}), contextFor("x", "remove"))).status).toBe(404);
    expect(decideProduct).not.toHaveBeenCalled();
  });

  it("başka siteden gelen isteği 403 ile reddeder", async () => {
    expect((await POST(aRequest("cross-site", { reason: "x" }), contextFor("9", "remove"))).status).toBe(403);
    expect(decideProduct).not.toHaveBeenCalled();
  });

  it("kaldırmayı gerekçesiyle iletir", async () => {
    decideProduct.mockResolvedValue({ id: 9, status: "REMOVED" });

    const response = await POST(aRequest("same-origin", { reason: "Fotoğraflar ürünü göstermiyor." }), contextFor("9", "remove"));

    expect(response.status).toBe(200);
    expect(decideProduct).toHaveBeenCalledWith("test-erisim", 9, "remove", "Fotoğraflar ürünü göstermiyor.");
  });
});
