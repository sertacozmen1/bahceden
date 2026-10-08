import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { SessionState } from "@/features/account/api";
import AdminProductsPage from "./page";

/**
 * Admin ilan listesi (product-listings spec'i "Admin ilanı kaldırır ve yeniden
 * açar", AGENTS.md §12.5). Oturum ve liste taklit edilir.
 */

const session = vi.hoisted(() => {
  const state: { current: SessionState } = { current: { kind: "anonymous" } };
  return state;
});
const listProductsForAdmin = vi.hoisted(() => vi.fn());

vi.mock("@/features/account/api", () => ({ currentSession: async () => session.current }));
vi.mock("@/features/admin/api", () => ({ adminProductStatuses: ["PUBLISHED", "REMOVED"], listProductsForAdmin }));

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
  listProductsForAdmin.mockReset();
});

describe("Admin ilan listesi", () => {
  it("admin rolü olmayan kullanıcıya bulunamadı gösterir", async () => {
    session.current = signedIn(["ROLE_SELLER"]);

    await expect(AdminProductsPage({ searchParams: Promise.resolve({}) })).rejects.toThrow("bulunamadı");
    expect(listProductsForAdmin).not.toHaveBeenCalled();
  });

  it("varsayılan olarak yayındaki ilanları vitrin bağlantısı ve kaldırma eylemiyle listeler", async () => {
    session.current = signedIn(["ROLE_ADMIN"]);
    listProductsForAdmin.mockResolvedValue({
      ...empty,
      totalElements: 1,
      totalPages: 1,
      items: [
        {
          id: 9,
          slug: "seftali",
          name: "Şeftali",
          status: "PUBLISHED",
          seller: { slug: "uretici", displayName: "Üretici Bahçesi", city: "Manisa" },
          updatedAt: "2026-10-08T09:30:00Z",
        },
      ],
    });

    renderWithIntl(await AdminProductsPage({ searchParams: Promise.resolve({}) }));

    expect(listProductsForAdmin).toHaveBeenCalledWith("test-erisim", "PUBLISHED", 0, 20);
    expect(screen.getByText("Şeftali")).toBeDefined();
    expect(screen.getByRole("link", { name: "Vitrinde gör" }).getAttribute("href")).toBe("/products/seftali");
    expect(screen.getByRole("button", { name: "Kaldır" })).toBeDefined();
    expect(screen.getByRole("link", { name: "İlanlar" }).getAttribute("aria-current")).toBe("page");
  });

  it("kaldırılan sekmesi gerekçeyi gösterir; boş listede boş durum", async () => {
    session.current = signedIn(["ROLE_ADMIN"]);
    listProductsForAdmin.mockResolvedValue(empty);

    renderWithIntl(await AdminProductsPage({ searchParams: Promise.resolve({ status: "REMOVED" }) }));

    expect(listProductsForAdmin).toHaveBeenCalledWith("test-erisim", "REMOVED", 0, 20);
    expect(screen.getByRole("heading", { name: "Bu durumda ilan yok" })).toBeDefined();
  });
});
