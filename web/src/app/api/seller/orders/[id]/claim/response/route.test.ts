import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Satıcı yanıtı Route Handler'ı (ADR-023, ADR-080). Siteler arası istek `api`'ye
 * ulaşmaz; yalnızca karar, satırlar ve gerekçe iletilir. `api` taklit edilir.
 */

const respondToClaim = vi.fn();

vi.mock("@/features/claims/api", () => ({ respondToClaim }));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim" }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { POST } = await import("./route");

function aRequest(site: string, body: unknown): Request {
  return new Request("http://localhost:3000/api/seller/orders/17/claim/response", {
    method: "POST",
    headers: { "Content-Type": "application/json", "sec-fetch-site": site },
    body: JSON.stringify(body),
  });
}

const context = (id: string) => ({ params: Promise.resolve({ id }) });
const body = { decision: "OFFER", lines: [{ orderItemId: 41, quantity: 1 }], reason: null, refundAmount: 999 };

beforeEach(() => {
  respondToClaim.mockReset();
});

describe("POST /api/seller/orders/[id]/claim/response", () => {
  it("başka siteden gelen isteği api'ye iletmeden 403 ile reddeder", async () => {
    expect((await POST(aRequest("cross-site", body), context("17"))).status).toBe(403);
    expect(respondToClaim).not.toHaveBeenCalled();
  });

  it("yalnızca karar, satırlar ve gerekçeyi iletir", async () => {
    respondToClaim.mockResolvedValue({ id: 17 });

    expect((await POST(aRequest("same-origin", body), context("17"))).status).toBe(200);
    expect(respondToClaim).toHaveBeenCalledWith("test-erisim", 17, {
      decision: "OFFER",
      lines: [{ orderItemId: 41, quantity: 1 }],
      reason: null,
    });
  });
});
