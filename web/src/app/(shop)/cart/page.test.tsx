import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { SessionState } from "@/features/account/api";
import type { CartView } from "@/features/cart/api";
import CartPage from "./page";

/**
 * Sepet sayfası (cart-and-checkout spec'i, AGENTS.md §12.5). Oturum ve sepet
 * taklit edilir; tutarlar sunucudan geldiği gibi gösterilir.
 */

const session = vi.hoisted(() => ({
  current: {
    kind: "signed-in",
    accessToken: "test-erisim",
    account: { email: "alici@ornek.test", roles: ["ROLE_CUSTOMER"], emailVerified: true },
  } as SessionState,
}));
const getCart = vi.hoisted(() => vi.fn());
const router = vi.hoisted(() => ({ refresh: vi.fn() }));

vi.mock("@/features/account/api", () => ({ currentSession: async () => session.current }));
vi.mock("@/features/cart/api", () => ({ getCart }));
vi.mock("next/navigation", () => ({ redirect: vi.fn(), useRouter: () => router }));

const money = (amount: number) => ({ amount, currency: "TRY" });

function aCart(overrides: Partial<CartView> = {}): CartView {
  return {
    groups: [
      {
        seller: { slug: "uretici", displayName: "Üretici Bahçesi", city: "Manisa" },
        lines: [
          {
            productId: 42,
            slug: "domates",
            name: "Domates",
            unit: "KILOGRAM",
            unitPrice: money(2450),
            quantity: 2,
            lineTotal: money(4900),
            stockQuantity: 10,
            available: true,
          },
          {
            productId: 43,
            slug: "biber",
            name: "Biber",
            unit: "KILOGRAM",
            unitPrice: money(3000),
            quantity: 1,
            lineTotal: money(3000),
            stockQuantity: 0,
            available: false,
          },
        ],
        itemsTotal: money(4900),
        shippingFee: money(9900),
        freeShippingRemaining: money(95100),
        dispatchDays: 2,
      },
    ],
    itemCount: 2,
    itemsTotal: money(4900),
    shippingTotal: money(9900),
    grandTotal: money(14800),
    hasUnavailableItems: true,
    ...overrides,
  };
}

beforeEach(() => {
  getCart.mockReset();
  router.refresh.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("Sepet sayfası", () => {
  it("satıcı grubunu, kargo ücretini, ücretsiz kargoya kalanı ve toplamı sunucudan geldiği gibi gösterir", async () => {
    getCart.mockResolvedValue(aCart());

    renderWithIntl(await CartPage());

    expect(screen.getByRole("heading", { name: "Üretici Bahçesi" })).toBeDefined();
    // Satır tutarı, grup ara toplamı ve sepet ürün toplamı aynı tutardır.
    expect(screen.getAllByText("₺49,00")).toHaveLength(3);
    expect(screen.getByText("Bu üreticiden ₺951,00 daha eklerseniz kargo ücretsiz.")).toBeDefined();
    expect(screen.getByText("Üretici 2 iş günü içinde kargoya verir")).toBeDefined();
    expect(screen.getByText("₺148,00")).toBeDefined();
  });

  it("satın alınamayan satırı işaretler, adet seçicisi göstermez ve sipariş öncesi uyarır", async () => {
    getCart.mockResolvedValue(aCart());

    renderWithIntl(await CartPage());

    expect(screen.getByText("Şu anda satın alınamıyor")).toBeDefined();
    expect(screen.queryByRole("group", { name: "Biber adedi" })).toBeNull();
    expect(screen.getByRole("group", { name: "Domates adedi" })).toBeDefined();
    expect(screen.getByText(/satın alınamayan ürünler var/)).toBeDefined();
  });

  it("satırı çıkarır ve sayfayı sunucudan yeniler", async () => {
    getCart.mockResolvedValue(aCart());
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(aCart()), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    renderWithIntl(await CartPage());
    fireEvent.click(screen.getByRole("button", { name: "Biber ürününü sepetten çıkar" }));

    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/cart/items/43");
    expect(init.method).toBe("DELETE");
  });

  it("boş sepette vitrine yönlendiren boş durum gösterir", async () => {
    getCart.mockResolvedValue(
      aCart({ groups: [], itemCount: 0, itemsTotal: money(0), shippingTotal: money(0), grandTotal: money(0), hasUnavailableItems: false }),
    );

    renderWithIntl(await CartPage());

    expect(screen.getByRole("heading", { name: "Sepetiniz boş" })).toBeDefined();
    expect(screen.getByRole("link", { name: "Vitrine dön" }).getAttribute("href")).toBe("/");
  });
});
