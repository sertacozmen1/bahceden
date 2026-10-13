import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Admin karar Route Handler'ı (ADR-086): yalnızca beyaz listedeki alanlar
 * `api`'ye iletilir; siteler arası istek ve geçersiz kimlik reddedilir.
 * `api` çağrısı taklit edilir.
 */

const decideClaim = vi.fn();

vi.mock("@/features/claims/api", () => ({ decideClaim }));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim" }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { POST } = await import("./route");

const context = (id: string) => ({ params: Promise.resolve({ id }) });

function post(body: unknown, site = "same-origin"): Request {
  return new Request("http://localhost:3000/api/admin/claims/5/decision", {
    method: "POST",
    headers: { "Content-Type": "application/json", "sec-fetch-site": site },
    body: JSON.stringify(body),
  });
}

beforeEach(() => {
  decideClaim.mockReset();
});

describe("POST /api/admin/claims/[id]/decision", () => {
  it("kararı, satırları ve gerekçeyi iletir", async () => {
    decideClaim.mockResolvedValue({ id: 5 });

    const response = await POST(post({ ...{ decision: "REJECT", reason: "Fotoğraflarda kusur görünmüyor." }, extra: "yok sayılır" }), context("5"));

    expect(response.status).toBe(200);
    expect(decideClaim).toHaveBeenCalledWith("test-erisim", 5, { decision: "REJECT", lines: undefined, reason: "Fotoğraflarda kusur görünmüyor." });
  });

  it("siteler arası istek reddedilir", async () => {
    expect((await POST(post({ decision: "REJECT", reason: "Fotoğraflarda kusur görünmüyor." }, "cross-site"), context("5"))).status).toBe(403);
    expect(decideClaim).not.toHaveBeenCalled();
  });

  it("geçersiz kimlik 404 olur", async () => {
    expect((await POST(post({ decision: "REJECT", reason: "Fotoğraflarda kusur görünmüyor." }), context("abc"))).status).toBe(404);
    expect(decideClaim).not.toHaveBeenCalled();
  });
});
