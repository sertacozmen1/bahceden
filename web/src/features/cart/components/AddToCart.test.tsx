import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import { AddToCart } from "./AddToCart";

/**
 * Ürün detayında sepete ekleme (cart-and-checkout spec'i, D10). Ağ ve
 * yönlendirici taklit edilir (AGENTS.md §12.6).
 */

const router = { refresh: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

beforeEach(() => {
  router.refresh.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("AddToCart", () => {
  it("adet stokla sınırlıdır: azaltma 1'de, artırma stokta durur", () => {
    renderWithIntl(<AddToCart productId={42} stock={2} />);

    expect(screen.getByRole("button", { name: "Adedi azalt" }).hasAttribute("disabled")).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: "Adedi artır" }));
    expect(screen.getByText("2")).toBeDefined();
    expect(screen.getByRole("button", { name: "Adedi artır" }).hasAttribute("disabled")).toBe(true);
  });

  it("seçilen adedi PUT ile gönderir, başarıyı ve sepete bağlantıyı gösterir, başlığı yeniler", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { groups: [], itemCount: 2 }));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<AddToCart productId={42} stock={10} />);

    fireEvent.click(screen.getByRole("button", { name: "Adedi artır" }));
    fireEvent.click(screen.getByRole("button", { name: "Sepete ekle" }));

    expect(await screen.findByText("Sepete eklendi.")).toBeDefined();
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/cart/items/42");
    expect(init.method).toBe("PUT");
    expect(JSON.parse(String(init.body))).toEqual({ quantity: 2 });
    expect(screen.getByRole("link", { name: "Sepete git" }).getAttribute("href")).toBe("/cart");
    expect(router.refresh).toHaveBeenCalled();
  });

  it("sunucunun hatasını gösterir (ör. satıcının kendi ürünü)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(422, { code: "CANNOT_BUY_OWN_PRODUCT", message: "Kendi ürününüzü satın alamazsınız.", fieldErrors: [] }),
      ),
    );
    renderWithIntl(<AddToCart productId={42} stock={10} />);

    fireEvent.click(screen.getByRole("button", { name: "Sepete ekle" }));

    expect(await screen.findByRole("alert")).toBeDefined();
    expect(screen.getByText("Kendi ürününüzü satın alamazsınız.")).toBeDefined();
    await waitFor(() => expect(router.refresh).not.toHaveBeenCalled());
  });
});
