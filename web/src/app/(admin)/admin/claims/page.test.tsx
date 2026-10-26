import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { SessionState } from "@/features/account/api";
import AdminClaimsPage from "./page";

/**
 * Admin sorun bildirimleri listesi (defect-claims spec'i, ADR-086,
 * AGENTS.md §12.5). Oturum ve liste taklit edilir.
 */

const session = vi.hoisted(() => {
  const state: { current: SessionState } = { current: { kind: "anonymous" } };
  return state;
});
const listClaimsForAdmin = vi.hoisted(() => vi.fn());

vi.mock("@/features/account/api", () => ({ currentSession: async () => session.current }));
vi.mock("@/features/claims/api", () => ({
  adminClaimStatuses: ["ESCALATED", "OPEN", "OFFERED", "REJECTED", "REFUNDED", "CLOSED"],
  listClaimsForAdmin,
}));

const notFound = vi.hoisted(() =>
  vi.fn(() => {
    throw new Error("bulunamadı");
  }),
);
vi.mock("next/navigation", () => ({ notFound, redirect: vi.fn(), useRouter: () => ({ refresh: vi.fn() }) }));

function signedIn(roles: ReadonlyArray<"ROLE_CUSTOMER" | "ROLE_SELLER" | "ROLE_ADMIN">): SessionState {
  return { kind: "signed-in", accessToken: "test-erisim", account: { email: "admin@ornek.test", roles: [...roles], emailVerified: true, twoFactorEnabled: false } };
}

const empty = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

beforeEach(() => {
  notFound.mockClear();
  listClaimsForAdmin.mockReset();
});

describe("Admin sorun bildirimleri", () => {
  it("admin rolü olmayan kullanıcıya bulunamadı gösterir", async () => {
    session.current = signedIn(["ROLE_SELLER"]);

    await expect(AdminClaimsPage({ searchParams: Promise.resolve({}) })).rejects.toThrow("bulunamadı");
    expect(listClaimsForAdmin).not.toHaveBeenCalled();
  });

  it("varsayılan olarak yönetime taşınanları detay bağlantısıyla listeler", async () => {
    session.current = signedIn(["ROLE_ADMIN"]);
    listClaimsForAdmin.mockResolvedValue({
      ...empty,
      totalElements: 1,
      totalPages: 1,
      items: [
        {
          id: 5,
          orderNumber: "BHC-7K2M9QXA4T",
          sellerOrderId: 17,
          sellerName: "Üretici Bahçesi",
          status: "ESCALATED",
          createdAt: "2026-10-13T10:00:00Z",
        },
      ],
    });

    renderWithIntl(await AdminClaimsPage({ searchParams: Promise.resolve({}) }));

    expect(listClaimsForAdmin).toHaveBeenCalledWith("test-erisim", "ESCALATED", 0, 20);
    expect(screen.getByRole("link", { name: /BHC-7K2M9QXA4T/ }).getAttribute("href")).toBe("/admin/claims/5");
    expect(screen.getByRole("link", { name: "Sorun bildirimleri" }).getAttribute("aria-current")).toBe("page");
  });

  it("iade edilmiş talepte tutarı gösterir; bilinmeyen sekmede varsayılanı kullanır", async () => {
    session.current = signedIn(["ROLE_ADMIN"]);
    listClaimsForAdmin.mockResolvedValue({
      ...empty,
      items: [
        {
          id: 6,
          orderNumber: "BHC-8L3N0RYB5U",
          sellerOrderId: 18,
          sellerName: "Üretici Bahçesi",
          status: "REFUNDED",
          createdAt: "2026-10-13T10:00:00Z",
          refundAmount: { amount: 4900, currency: "TRY" },
        },
      ],
    });

    renderWithIntl(await AdminClaimsPage({ searchParams: Promise.resolve({ status: "DELETED" }) }));

    expect(listClaimsForAdmin).toHaveBeenCalledWith("test-erisim", "ESCALATED", 0, 20);
    expect(screen.getByText("İade: ₺49,00")).toBeDefined();
  });

  it("boş durumda yönlendirici metin gösterir", async () => {
    session.current = signedIn(["ROLE_ADMIN"]);
    listClaimsForAdmin.mockResolvedValue(empty);

    renderWithIntl(await AdminClaimsPage({ searchParams: Promise.resolve({ status: "CLOSED" }) }));

    expect(listClaimsForAdmin).toHaveBeenCalledWith("test-erisim", "CLOSED", 0, 20);
    expect(screen.getByText("Bu sekmede talep yok")).toBeDefined();
  });
});
