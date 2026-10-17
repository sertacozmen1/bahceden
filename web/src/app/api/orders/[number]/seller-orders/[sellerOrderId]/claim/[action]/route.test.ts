import { beforeEach, describe, expect, it, vi } from "vitest";

/** Alıcının talep eylemi Route Handler'ı (ADR-023, ADR-080): eylem beyaz listeden. */

const buyerClaimAction = vi.fn();

vi.mock("@/features/claims/api", () => ({ buyerClaimAction, buyerClaimActions: ["accept", "escalate"] }));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim" }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { POST } = await import("./route");

function aRequest(): Request {
  return new Request("http://localhost:3000/api/orders/BHC-1/seller-orders/17/claim/accept", {
    method: "POST",
    headers: { "Content-Type": "application/json", "sec-fetch-site": "same-origin" },
    body: "{}",
  });
}

const context = (action: string, sellerOrderId = "17") => ({
  params: Promise.resolve({ number: "BHC-1", sellerOrderId, action }),
});

beforeEach(() => {
  buyerClaimAction.mockReset();
});

describe("POST /api/orders/[number]/seller-orders/[sellerOrderId]/claim/[action]", () => {
  it("bilinmeyen eylemi ve geçersiz kimliği api'ye iletmeden 404 döner", async () => {
    expect((await POST(aRequest(), context("refund"))).status).toBe(404);
    expect((await POST(aRequest(), context("accept", "abc"))).status).toBe(404);
    expect(buyerClaimAction).not.toHaveBeenCalled();
  });

  it("geçerli eylemi iletir", async () => {
    buyerClaimAction.mockResolvedValue({ orderNumber: "BHC-1" });

    expect((await POST(aRequest(), context("escalate"))).status).toBe(200);
    expect(buyerClaimAction).toHaveBeenCalledWith("test-erisim", "BHC-1", 17, "escalate");
  });
});
