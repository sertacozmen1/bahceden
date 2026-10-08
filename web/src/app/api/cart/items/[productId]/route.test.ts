import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Sepet satırı Route Handler'ı (ADR-023). Siteler arası istek `api`'ye
 * ulaşmaz; gövdeden yalnızca adet iletilir; geçersiz kimlik `api`'ye gitmez.
 */

const setCartItem = vi.fn();
const removeCartItem = vi.fn();

vi.mock("@/features/cart/api", () => ({ setCartItem, removeCartItem }));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim" }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { PUT, DELETE } = await import("./route");

function aRequest(method: string, site: string, body?: unknown): Request {
  return new Request("http://localhost:3000/api/cart/items/42", {
    method,
    headers: { "Content-Type": "application/json", "sec-fetch-site": site },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

const context = (productId: string) => ({ params: Promise.resolve({ productId }) });

beforeEach(() => {
  setCartItem.mockReset();
  removeCartItem.mockReset();
});

describe("/api/cart/items/[productId]", () => {
  it("başka siteden gelen isteği api'ye iletmeden 403 ile reddeder", async () => {
    expect((await PUT(aRequest("PUT", "cross-site", { quantity: 1 }), context("42"))).status).toBe(403);
    expect((await DELETE(aRequest("DELETE", "cross-site"), context("42"))).status).toBe(403);
    expect(setCartItem).not.toHaveBeenCalled();
    expect(removeCartItem).not.toHaveBeenCalled();
  });

  it("yalnızca adedi iletir", async () => {
    setCartItem.mockResolvedValue({ itemCount: 1 });

    const response = await PUT(aRequest("PUT", "same-origin", { quantity: 3, price: 1 }), context("42"));

    expect(response.status).toBe(200);
    expect(setCartItem).toHaveBeenCalledWith("test-erisim", 42, { quantity: 3 });
  });

  it("geçersiz ürün kimliği 404 olur ve api'ye gitmez", async () => {
    expect((await PUT(aRequest("PUT", "same-origin", { quantity: 1 }), context("abc"))).status).toBe(404);
    expect((await DELETE(aRequest("DELETE", "same-origin"), context("-1"))).status).toBe(404);
    expect(setCartItem).not.toHaveBeenCalled();
  });
});
