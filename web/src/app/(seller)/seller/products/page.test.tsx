import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { SessionState } from "@/features/account/api";
import { ApiRequestError } from "@/lib/apiError";
import SellerProductsPage from "./page";

/**
 * Satıcının ilan listesi (product-listings spec'i, AGENTS.md §12.5). Oturum
 * ve liste taklit edilir; sunucuya çıkılmaz.
 */

const session = vi.hoisted(() => {
  const state: { current: SessionState } = { current: { kind: "anonymous" } };
  return state;
});
const listOwnListings = vi.hoisted(() => vi.fn());
const getOwnPayoutAccount = vi.hoisted(() => vi.fn());

vi.mock("@/features/account/api", () => ({ currentSession: async () => session.current }));
vi.mock("@/features/listings/api", () => ({ listOwnListings }));
vi.mock("@/features/payout/api", () => ({
  getOwnPayoutAccount,
  acceptsPayments: (account: { status: string } | null) => account?.status === "ACTIVE",
}));

const notFound = vi.hoisted(() =>
  vi.fn(() => {
    throw new Error("bulunamadı");
  }),
);
vi.mock("next/navigation", () => ({ notFound, redirect: vi.fn() }));

function signedIn(roles: ReadonlyArray<"ROLE_CUSTOMER" | "ROLE_SELLER" | "ROLE_ADMIN">): SessionState {
  return {
    kind: "signed-in",
    accessToken: "test-erisim",
    account: { email: "uretici@ornek.test", roles: [...roles], emailVerified: true, twoFactorEnabled: false },
  };
}

const empty = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

beforeEach(() => {
  notFound.mockClear();
  listOwnListings.mockReset();
  getOwnPayoutAccount.mockReset();
  getOwnPayoutAccount.mockResolvedValue({ status: "ACTIVE" });
});

describe("Ürünlerim sayfası", () => {
  it("satıcı rolü olmayan kullanıcıya bulunamadı gösterir", async () => {
    session.current = signedIn(["ROLE_CUSTOMER"]);

    await expect(SellerProductsPage({ searchParams: Promise.resolve({}) })).rejects.toThrow("bulunamadı");
    expect(listOwnListings).not.toHaveBeenCalled();
  });

  it("ilanları fiyat, stok ve durumla listeler", async () => {
    session.current = signedIn(["ROLE_CUSTOMER", "ROLE_SELLER"]);
    listOwnListings.mockResolvedValue({
      ...empty,
      totalElements: 1,
      totalPages: 1,
      items: [
        {
          id: 7,
          slug: "salkim-domates",
          name: "Salkım Domates",
          unit: "KILOGRAM",
          unitPrice: { amount: 2450, currency: "TRY" },
          stockQuantity: 80,
          status: "PUBLISHED",
          updatedAt: "2026-10-08T09:30:00Z",
        },
      ],
    });

    renderWithIntl(await SellerProductsPage({ searchParams: Promise.resolve({}) }));

    expect(screen.getByRole("link", { name: /Salkım Domates/ }).getAttribute("href")).toBe("/seller/products/7");
    expect(screen.getByText("Yayında")).toBeDefined();
    expect(screen.getByText(/Stok: 80/)).toBeDefined();
    expect(screen.getByRole("link", { name: "Yeni ilan" }).getAttribute("href")).toBe("/seller/products/new");
  });

  it("ilan yoksa yönlendirici boş durum gösterir", async () => {
    session.current = signedIn(["ROLE_SELLER"]);
    listOwnListings.mockResolvedValue(empty);

    renderWithIntl(await SellerProductsPage({ searchParams: Promise.resolve({}) }));

    expect(screen.getByRole("heading", { name: "Henüz ilanınız yok" })).toBeDefined();
    expect(screen.queryByText("Ürünleriniz henüz satın alınamıyor")).toBeNull();
  });

  it("ödeme hesabı yoksa ilanların satın alınamayacağını söyler ve ödeme bilgilerine götürür", async () => {
    session.current = signedIn(["ROLE_SELLER"]);
    listOwnListings.mockResolvedValue(empty);
    getOwnPayoutAccount.mockResolvedValue(null);

    renderWithIntl(await SellerProductsPage({ searchParams: Promise.resolve({}) }));

    expect(screen.getByText("Ürünleriniz henüz satın alınamıyor")).toBeDefined();
    expect(screen.getByRole("link", { name: "Ödeme bilgilerine git" }).getAttribute("href")).toBe("/seller/payout");
  });

  it("api satıcıyı onaylı saymazsa hata yerine başvuru yönlendirmesi gösterir", async () => {
    session.current = signedIn(["ROLE_SELLER"]);
    listOwnListings.mockRejectedValue(
      new ApiRequestError(403, { code: "SELLER_NOT_APPROVED", message: "Onaylı satıcı gerekiyor.", fieldErrors: [] }),
    );

    renderWithIntl(await SellerProductsPage({ searchParams: Promise.resolve({}) }));

    expect(screen.getByRole("heading", { name: "Satıcı kaydınız onaylı değil" })).toBeDefined();
    expect(screen.getByRole("link", { name: "Başvuru durumunu gör" }).getAttribute("href")).toBe("/seller/application");
  });
});
