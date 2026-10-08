import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { SessionState } from "@/features/account/api";
import MyOrdersPage from "./page";

/** Siparişlerim (orders spec'i "Alıcı kendi siparişlerini görür"). Veri taklit edilir. */

const session = vi.hoisted(() => ({
  current: {
    kind: "signed-in",
    accessToken: "test-erisim",
    account: { email: "alici@ornek.test", roles: ["ROLE_CUSTOMER"], emailVerified: true },
  } as SessionState,
}));
const listOwnOrders = vi.hoisted(() => vi.fn());

vi.mock("@/features/account/api", () => ({ currentSession: async () => session.current }));
vi.mock("@/features/orders/api", () => ({ listOwnOrders }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));

const empty = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

beforeEach(() => {
  listOwnOrders.mockReset();
});

describe("Siparişlerim", () => {
  it("siparişleri numara, toplam ve durumla listeler ve detaya bağlar", async () => {
    listOwnOrders.mockResolvedValue({
      ...empty,
      totalElements: 1,
      totalPages: 1,
      items: [
        { orderNumber: "BHC-7K2M9QXA4T", status: "PAID", grandTotal: { amount: 12350, currency: "TRY" }, createdAt: "2026-10-08T09:30:00Z" },
      ],
    });

    renderWithIntl(await MyOrdersPage({ searchParams: Promise.resolve({}) }));

    expect(screen.getByRole("link", { name: /BHC-7K2M9QXA4T/ }).getAttribute("href")).toBe("/account/orders/BHC-7K2M9QXA4T");
    expect(screen.getByText("Ödendi")).toBeDefined();
    expect(screen.getByText(/₺123,50/)).toBeDefined();
  });

  it("sipariş yoksa vitrine yönlendiren boş durum gösterir", async () => {
    listOwnOrders.mockResolvedValue(empty);

    renderWithIntl(await MyOrdersPage({ searchParams: Promise.resolve({}) }));

    expect(screen.getByRole("heading", { name: "Henüz siparişiniz yok" })).toBeDefined();
    expect(screen.getByRole("link", { name: "Vitrine dön" }).getAttribute("href")).toBe("/");
  });
});
