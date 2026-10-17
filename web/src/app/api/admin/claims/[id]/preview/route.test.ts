import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Admin önizleme Route Handler'ı (ADR-086): yalnızca beyaz listedeki alanlar
 * `api`'ye iletilir; siteler arası istek ve geçersiz kimlik reddedilir.
 * `api` çağrısı taklit edilir.
 */

const previewClaimForAdmin = vi.fn();

vi.mock("@/features/claims/api", () => ({ previewClaimForAdmin }));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim" }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { POST } = await import("./route");

const context = (id: string) => ({ params: Promise.resolve({ id }) });

function post(body: unknown, site = "same-origin"): Request {
  return new Request("http://localhost:3000/api/admin/claims/5/preview", {
    method: "POST",
    headers: { "Content-Type": "application/json", "sec-fetch-site": site },
    body: JSON.stringify(body),
  });
}

beforeEach(() => {
  previewClaimForAdmin.mockReset();
});

describe("POST /api/admin/claims/[id]/preview", () => {
  it("yalnızca satırları iletir", async () => {
    previewClaimForAdmin.mockResolvedValue({ id: 5 });

    const response = await POST(post({ ...{ lines: [{ orderItemId: 41, quantity: 1 }] }, extra: "yok sayılır" }), context("5"));

    expect(response.status).toBe(200);
    expect(previewClaimForAdmin).toHaveBeenCalledWith("test-erisim", 5, { lines: [{ orderItemId: 41, quantity: 1 }] });
  });

  it("siteler arası istek reddedilir", async () => {
    expect((await POST(post({ lines: [{ orderItemId: 41, quantity: 1 }] }, "cross-site"), context("5"))).status).toBe(403);
    expect(previewClaimForAdmin).not.toHaveBeenCalled();
  });

  it("geçersiz kimlik 404 olur", async () => {
    expect((await POST(post({ lines: [{ orderItemId: 41, quantity: 1 }] }), context("abc"))).status).toBe(404);
    expect(previewClaimForAdmin).not.toHaveBeenCalled();
  });
});
