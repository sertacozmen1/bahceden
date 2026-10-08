import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Sipariş oluşturma Route Handler'ı (ADR-023). Siteler arası istek `api`'ye
 * ulaşmaz; gövdeden yalnızca adres kimliği, başlıklardan yalnızca tekrar
 * anahtarı iletilir. `api` taklit edilir (K10).
 */

const placeOrder = vi.fn();

vi.mock("@/features/orders/api", () => ({ placeOrder }));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim" }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { POST } = await import("./route");

function aRequest(site: string, body: unknown, key?: string): Request {
  const headers: Record<string, string> = { "Content-Type": "application/json", "sec-fetch-site": site };
  if (key !== undefined) {
    headers["Idempotency-Key"] = key;
  }
  return new Request("http://localhost:3000/api/orders", { method: "POST", headers, body: JSON.stringify(body) });
}

beforeEach(() => {
  placeOrder.mockReset();
});

describe("POST /api/orders", () => {
  it("başka siteden gelen isteği api'ye iletmeden 403 ile reddeder", async () => {
    expect((await POST(aRequest("cross-site", { addressId: 5 }, "anahtar-0001"))).status).toBe(403);
    expect(placeOrder).not.toHaveBeenCalled();
  });

  it("tekrar anahtarını ve yalnızca adres kimliğini iletir, 201'i korur", async () => {
    placeOrder.mockResolvedValue({ status: 201, placed: { orderNumber: "BHC-1", paymentRedirectUrl: "/odeme" } });

    const response = await POST(aRequest("same-origin", { addressId: 5, grandTotal: 1 }, "anahtar-0001"));

    expect(response.status).toBe(201);
    expect(placeOrder).toHaveBeenCalledWith("test-erisim", "anahtar-0001", { addressId: 5 });
  });

  it("anahtar yoksa boş anahtar iletir; doğrulama api'dedir", async () => {
    placeOrder.mockResolvedValue({ status: 201, placed: {} });

    await POST(aRequest("same-origin", { addressId: 5 }));

    expect(placeOrder).toHaveBeenCalledWith("test-erisim", "", { addressId: 5 });
  });
});
