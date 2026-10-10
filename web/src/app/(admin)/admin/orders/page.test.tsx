import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { SessionState } from "@/features/account/api";
import AdminOrdersPage from "./page";

/**
 * Admin teslim edilemeyen siparişler listesi (shipments spec'i, add-shipments
 * D7, AGENTS.md §12.5). Oturum ve liste taklit edilir.
 */

const session = vi.hoisted(() => {
  const state: { current: SessionState } = { current: { kind: "anonymous" } };
  return state;
});
const listUndeliveredForAdmin = vi.hoisted(() => vi.fn());

vi.mock("@/features/account/api", () => ({ currentSession: async () => session.current }));
vi.mock("@/features/admin/api", () => ({
  adminSellerOrderStatuses: ["DELIVERY_FAILED", "REFUNDED", "CLOSED"],
  listUndeliveredForAdmin,
}));

const notFound = vi.hoisted(() =>
  vi.fn(() => {
    throw new Error("bulunamadı");
  }),
);
vi.mock("next/navigation", () => ({ notFound, redirect: vi.fn(), useRouter: () => ({ refresh: vi.fn() }) }));

function signedIn(roles: ReadonlyArray<"ROLE_CUSTOMER" | "ROLE_SELLER" | "ROLE_ADMIN">): SessionState {
  return { kind: "signed-in", accessToken: "test-erisim", account: { email: "admin@ornek.test", roles: [...roles], emailVerified: true } };
}

const empty = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

beforeEach(() => {
  notFound.mockClear();
  listUndeliveredForAdmin.mockReset();
});

describe("Admin teslim edilemeyen siparişler", () => {
  it("admin rolü olmayan kullanıcıya bulunamadı gösterir", async () => {
    session.current = signedIn(["ROLE_SELLER"]);

    await expect(AdminOrdersPage({ searchParams: Promise.resolve({}) })).rejects.toThrow("bulunamadı");
    expect(listUndeliveredForAdmin).not.toHaveBeenCalled();
  });

  it("varsayılan olarak karar bekleyenleri iade tutarı ve detay bağlantısıyla listeler", async () => {
    session.current = signedIn(["ROLE_ADMIN"]);
    listUndeliveredForAdmin.mockResolvedValue({
      ...empty,
      totalElements: 1,
      totalPages: 1,
      items: [
        {
          id: 17,
          orderNumber: "BHC-7K2M9QXA4T",
          status: "DELIVERY_FAILED",
          sellerName: "Üretici Bahçesi",
          refundableAmount: { amount: 14800, currency: "TRY" },
          createdAt: "2026-10-08T09:30:00Z",
        },
      ],
    });

    renderWithIntl(await AdminOrdersPage({ searchParams: Promise.resolve({}) }));

    expect(listUndeliveredForAdmin).toHaveBeenCalledWith("test-erisim", "DELIVERY_FAILED", 0, 20);
    expect(screen.getByRole("link", { name: /BHC-7K2M9QXA4T/ }).getAttribute("href")).toBe("/admin/orders/17");
    expect(screen.getByText("İade tutarı: ₺148,00")).toBeDefined();
    expect(screen.getByRole("link", { name: "Teslim edilemeyenler" }).getAttribute("aria-current")).toBe("page");
  });

  it("boş durumda yönlendirici metin gösterir", async () => {
    session.current = signedIn(["ROLE_ADMIN"]);
    listUndeliveredForAdmin.mockResolvedValue(empty);

    renderWithIntl(await AdminOrdersPage({ searchParams: Promise.resolve({ status: "CLOSED" }) }));

    expect(listUndeliveredForAdmin).toHaveBeenCalledWith("test-erisim", "CLOSED", 0, 20);
    expect(screen.getByText("Bu durumda sipariş yok")).toBeDefined();
  });
});
