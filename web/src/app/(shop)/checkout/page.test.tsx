import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { SessionState } from "@/features/account/api";
import type { CartView } from "@/features/cart/api";
import CheckoutPage from "./page";

/**
 * Ödeme sayfasının e-posta doğrulama kapısı (cart-and-checkout spec'i
 * "Doğrulama kapısı ödeme sayfasında anlatılır", add-checkout-email-verification
 * D2). Oturum, sepet ve adresler taklit edilir; sunucuya çıkılmaz.
 */

const session = vi.hoisted(() => ({ current: { kind: "anonymous" } as SessionState }));
const getCart = vi.hoisted(() => vi.fn());

vi.mock("@/features/account/api", () => ({ currentSession: async () => session.current }));
vi.mock("@/features/cart/api", () => ({ getCart }));
vi.mock("@/features/addresses/api", () => ({
  listAddresses: async () => [],
  listDeliveryProvinces: async () => [{ code: "35", name: "İzmir" }],
}));
vi.mock("next/navigation", () => ({
  redirect: vi.fn((target: string) => {
    throw new Error(`yönlendirildi: ${target}`);
  }),
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

const money = (amount: number) => ({ amount, currency: "TRY" });

function aCart(): CartView {
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
            quantity: 1,
            lineTotal: money(2450),
            stockQuantity: 10,
            available: true,
          },
        ],
        itemsTotal: money(2450),
        shippingFee: money(9900),
        freeShippingRemaining: money(97550),
        dispatchDays: 2,
      },
    ],
    itemCount: 1,
    itemsTotal: money(2450),
    shippingTotal: money(9900),
    grandTotal: money(12350),
    hasUnavailableItems: false,
  };
}

function signedIn(emailVerified: boolean): SessionState {
  return {
    kind: "signed-in",
    accessToken: "test-erisim",
    account: { email: "alici@ornek.test", roles: ["ROLE_CUSTOMER"], emailVerified },
  };
}

beforeEach(() => {
  getCart.mockReset();
  getCart.mockResolvedValue(aCart());
});

describe("Ödeme sayfası", () => {
  it("e-posta doğrulanmamışsa özeti gösterir, form yerine doğrulama yönlendirmesi ve yeni bağlantı düğmesi sunar", async () => {
    session.current = signedIn(false);

    renderWithIntl(await CheckoutPage());

    expect(screen.getByRole("heading", { name: "Üretici Bahçesi" })).toBeDefined();
    expect(screen.getByRole("heading", { name: "Önce e-postanızı doğrulayın" })).toBeDefined();
    expect(screen.getByRole("button", { name: "Yeni doğrulama bağlantısı gönder" })).toBeDefined();
    expect(screen.queryByRole("button", { name: "Ödemeye geç" })).toBeNull();
  });

  it("e-posta doğrulanmışsa ödeme formunu gösterir", async () => {
    session.current = signedIn(true);

    renderWithIntl(await CheckoutPage());

    expect(screen.getByRole("button", { name: "Ödemeye geç" })).toBeDefined();
    expect(screen.queryByRole("heading", { name: "Önce e-postanızı doğrulayın" })).toBeNull();
  });
});
