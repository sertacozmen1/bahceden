import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Admin karar Route Handler'ı (add-shipments D7): karar beyaz listede değilse
 * `api`'ye gidilmez; gerekçe kırpılarak iletilir. `api` çağrısı taklit edilir.
 */

const resolveUndelivered = vi.fn();

vi.mock("@/features/admin/api", () => ({ resolveUndelivered, undeliveredDecisions: ["REFUND", "CLOSE"] }));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim" }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { POST } = await import("./route");

const context = (id: string) => ({ params: Promise.resolve({ id }) });

function post(body: unknown): Request {
  return new Request("http://localhost:3000/api/admin/seller-orders/17/resolution", {
    method: "POST",
    headers: { "Content-Type": "application/json", "sec-fetch-site": "same-origin" },
    body: JSON.stringify(body),
  });
}

beforeEach(() => {
  resolveUndelivered.mockReset();
});

describe("POST /api/admin/seller-orders/[id]/resolution", () => {
  it("kararı ve kırpılmış gerekçeyi iletir", async () => {
    resolveUndelivered.mockResolvedValue({ id: 17, status: "CLOSED" });

    const response = await POST(post({ decision: "CLOSE", reason: "  Alıcı adreste yoktu.  " }), context("17"));

    expect(response.status).toBe(200);
    expect(resolveUndelivered).toHaveBeenCalledWith("test-erisim", 17, "CLOSE", "Alıcı adreste yoktu.");
  });

  it("bilinmeyen karar 400 olur ve api'ye gidilmez", async () => {
    const response = await POST(post({ decision: "DELETE" }), context("17"));

    expect(response.status).toBe(400);
    expect(resolveUndelivered).not.toHaveBeenCalled();
  });

  it("siteler arası istek reddedilir", async () => {
    const request = new Request("http://localhost:3000/api/admin/seller-orders/17/resolution", {
      method: "POST",
      headers: { "Content-Type": "application/json", "sec-fetch-site": "cross-site" },
      body: JSON.stringify({ decision: "REFUND" }),
    });

    expect((await POST(request, context("17"))).status).toBe(403);
    expect(resolveUndelivered).not.toHaveBeenCalled();
  });

  it("geçersiz kimlik 404 olur", async () => {
    const response = await POST(post({ decision: "REFUND" }), context("0"));

    expect(response.status).toBe(404);
    expect(resolveUndelivered).not.toHaveBeenCalled();
  });
});
