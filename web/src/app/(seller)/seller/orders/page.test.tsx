import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { SessionState } from "@/features/account/api";
import { ApiRequestError } from "@/lib/apiError";
import SellerOrdersPage from "./page";

/** Gelen siparişler (orders spec'i "Satıcı kendi satıcı siparişlerini görür"). Veri taklit edilir. */

const session = vi.hoisted(() => ({
  current: {
    kind: "signed-in",
    accessToken: "test-erisim",
    account: { email: "uretici@ornek.test", roles: ["ROLE_CUSTOMER", "ROLE_SELLER"], emailVerified: true },
  } as SessionState,
}));
const listSellerOrders = vi.hoisted(() => vi.fn());

vi.mock("@/features/account/api", () => ({ currentSession: async () => session.current }));
vi.mock("@/features/orders/api", () => ({ listSellerOrders, sellerOrderFilters: ["NEW", "CANCELLED"] }));
vi.mock("next/navigation", () => ({ redirect: vi.fn(), notFound: vi.fn() }));

const empty = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

beforeEach(() => {
  listSellerOrders.mockReset();
});

describe("Gelen siparişler", () => {
  it("varsayılan olarak yeni siparişleri ister; hakedişi ve süre aşımını gösterir", async () => {
    listSellerOrders.mockResolvedValue({
      ...empty,
      totalElements: 1,
      totalPages: 1,
      items: [
        {
          id: 17,
          orderNumber: "BHC-7K2M9QXA4T",
          status: "NEW",
          itemsTotal: { amount: 24550, currency: "TRY" },
          sellerPayout: { amount: 23022, currency: "TRY" },
          dispatchDueAt: "2026-10-13T20:59:59Z",
          dispatchOverdue: true,
          createdAt: "2026-10-08T09:30:00Z",
        },
      ],
    });

    renderWithIntl(await SellerOrdersPage({ searchParams: Promise.resolve({}) }));

    expect(listSellerOrders).toHaveBeenCalledWith("test-erisim", "NEW", 0, 20);
    expect(screen.getByRole("link", { name: /BHC-7K2M9QXA4T/ }).getAttribute("href")).toBe("/seller/orders/17");
    expect(screen.getByText("Hakediş: ₺230,22")).toBeDefined();
    expect(screen.getByText(/kargoya verme süresini aştı/)).toBeDefined();
    expect(screen.getByRole("link", { name: "İptal edildi" }).getAttribute("href")).toBe("/seller/orders?status=CANCELLED");
  });

  it("api satıcıyı onaylı saymazsa başvuru yönlendirmesi gösterir", async () => {
    listSellerOrders.mockRejectedValue(
      new ApiRequestError(403, { code: "SELLER_NOT_APPROVED", message: "Onaylı satıcı gerekiyor.", fieldErrors: [] }),
    );

    renderWithIntl(await SellerOrdersPage({ searchParams: Promise.resolve({ status: "CANCELLED" }) }));

    expect(screen.getByRole("heading", { name: "Satıcı kaydınız onaylı değil" })).toBeDefined();
  });
});
