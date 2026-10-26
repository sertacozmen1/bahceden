import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { SessionState } from "@/features/account/api";
import type { AdminSellerPage } from "@/features/admin/api";
import AdminSellersPage from "./page";

/**
 * Admin satıcı listesi (seller-onboarding spec'i "Admin başvuruları karara
 * bağlar", AGENTS.md §12.5). Oturum ve liste taklit edilir; sunucuya çıkılmaz.
 */

const session = vi.hoisted(() => {
  const state: { current: SessionState } = { current: { kind: "anonymous" } };
  return state;
});
const listSellersForAdmin = vi.hoisted(() => vi.fn());

vi.mock("@/features/account/api", () => ({ currentSession: async () => session.current }));
vi.mock("@/features/admin/api", () => ({
  adminSellerStatuses: ["PENDING", "APPROVED", "REJECTED", "SUSPENDED"],
  listSellersForAdmin,
}));

const notFound = vi.hoisted(() =>
  vi.fn(() => {
    throw new Error("bulunamadı");
  }),
);
vi.mock("next/navigation", () => ({ notFound, redirect: vi.fn() }));

function signedIn(roles: ReadonlyArray<"ROLE_CUSTOMER" | "ROLE_SELLER" | "ROLE_ADMIN">): SessionState {
  return { kind: "signed-in", accessToken: "test-erisim", account: { email: "admin@ornek.test", roles: [...roles], emailVerified: true, twoFactorEnabled: false } };
}

function aPage(items: AdminSellerPage["items"]): AdminSellerPage {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: items.length === 0 ? 0 : 1 };
}

beforeEach(() => {
  notFound.mockClear();
  listSellersForAdmin.mockReset();
});

describe("Admin satıcı listesi", () => {
  it("admin rolü olmayan kullanıcıya bulunamadı gösterir ve listeyi istemez", async () => {
    session.current = signedIn(["ROLE_CUSTOMER"]);

    await expect(AdminSellersPage({ searchParams: Promise.resolve({}) })).rejects.toThrow("bulunamadı");
    expect(listSellersForAdmin).not.toHaveBeenCalled();
  });

  it("varsayılan olarak bekleyenleri listeler; satırda kişisel veri yok", async () => {
    session.current = signedIn(["ROLE_CUSTOMER", "ROLE_ADMIN"]);
    listSellersForAdmin.mockResolvedValue(
      aPage([
        {
          id: 7,
          displayName: "Örnek Bahçe",
          city: "Manisa",
          status: "PENDING",
          createdAt: "2026-10-08T09:30:00Z",
          statusChangedAt: "2026-10-08T09:30:00Z",
        },
      ]),
    );

    renderWithIntl(await AdminSellersPage({ searchParams: Promise.resolve({}) }));

    expect(listSellersForAdmin).toHaveBeenCalledWith("test-erisim", "PENDING", 0, 20);
    expect(screen.getByRole("link", { name: /Örnek Bahçe/ }).getAttribute("href")).toBe("/admin/sellers/7");
    expect(screen.getByRole("link", { name: "Bekleyen" }).getAttribute("aria-current")).toBe("page");
  });

  it("geçersiz süzgeç bekleyenlere düşer; boş listede yönlendirici boş durum gösterir", async () => {
    session.current = signedIn(["ROLE_ADMIN"]);
    listSellersForAdmin.mockResolvedValue(aPage([]));

    renderWithIntl(await AdminSellersPage({ searchParams: Promise.resolve({ status: "BOGUS", page: "-3" }) }));

    expect(listSellersForAdmin).toHaveBeenCalledWith("test-erisim", "PENDING", 0, 20);
    expect(screen.getByRole("heading", { name: "Bu durumda kayıt yok" })).toBeDefined();
  });
});
