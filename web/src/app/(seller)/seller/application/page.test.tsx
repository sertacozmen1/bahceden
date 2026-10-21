import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { SessionState } from "@/features/account/api";
import type { SellerSelfView } from "@/features/seller/api";
import SellerApplicationPage from "./page";

/**
 * Başvuru sayfasının durum ekranları (seller-onboarding spec'i, AGENTS.md
 * §12.5). Oturum ve kayıt okuma taklit edilir; sunucuya çıkılmaz.
 */

const session = vi.hoisted(() => {
  const state: { current: SessionState } = { current: { kind: "anonymous" } };
  return state;
});
const ownSeller = vi.hoisted(() => {
  const state: { current: SellerSelfView | null } = { current: null };
  return state;
});

vi.mock("@/features/account/api", () => ({ currentSession: async () => session.current }));
vi.mock("@/features/seller/api", () => ({
  getOwnSeller: async () => ownSeller.current,
  sellerDocumentTypes: ["CKS_CERTIFICATE", "TAX_CERTIFICATE"],
}));

const redirect = vi.hoisted(() =>
  vi.fn((target: string) => {
    throw new Error(`yönlendirildi: ${target}`);
  }),
);
vi.mock("next/navigation", () => ({ redirect, useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));
vi.mock("next/headers", () => ({ headers: async () => new Headers() }));

function signedIn(emailVerified: boolean): SessionState {
  return {
    kind: "signed-in",
    accessToken: "test-erisim",
    account: { email: "uretici@ornek.test", roles: ["ROLE_CUSTOMER"], emailVerified },
  };
}

function aSeller(overrides: Partial<SellerSelfView>): SellerSelfView {
  return {
    slug: "ornek-bahce",
    displayName: "Örnek Bahçe",
    city: "Manisa",
    phone: "+905550000000",
    status: "PENDING",
    documentType: "CKS_CERTIFICATE",
    statusChangedAt: "2026-10-08T09:30:00Z",
    ...overrides,
  };
}

beforeEach(() => {
  redirect.mockClear();
  ownSeller.current = null;
});

describe("Satıcı başvurusu sayfası", () => {
  it("oturum yoksa giriş sayfasına, dönüş yoluyla yönlendirir", async () => {
    session.current = { kind: "anonymous" };

    await expect(SellerApplicationPage()).rejects.toThrow("yönlendirildi");
    expect(redirect).toHaveBeenCalledWith("/login?next=%2Fseller%2Fapplication");
  });

  it("e-posta doğrulanmamışsa form yerine doğrulama yönlendirmesi gösterir", async () => {
    session.current = signedIn(false);

    renderWithIntl(await SellerApplicationPage());

    expect(screen.getByRole("heading", { name: "Önce e-postanızı doğrulayın" })).toBeDefined();
    expect(screen.getByRole("button", { name: "Yeni doğrulama bağlantısı gönder" })).toBeDefined();
    expect(screen.queryByRole("button", { name: "Başvuruyu gönder" })).toBeNull();
  });

  it("kayıt yoksa başvuru formunu gösterir", async () => {
    session.current = signedIn(true);

    renderWithIntl(await SellerApplicationPage());

    expect(screen.getByRole("heading", { name: "Satıcı başvurusu" })).toBeDefined();
    expect(screen.getByRole("button", { name: "Başvuruyu gönder" })).toBeDefined();
  });

  it("bekleyen başvuruda durumu, beyanı ve belge bağlantısını gösterir", async () => {
    session.current = signedIn(true);
    ownSeller.current = aSeller({ status: "PENDING" });

    renderWithIntl(await SellerApplicationPage());

    expect(screen.getByRole("heading", { name: "Başvurunuz inceleniyor" })).toBeDefined();
    expect(screen.getByText("İnceleniyor")).toBeDefined();
    expect(screen.getByText("Örnek Bahçe")).toBeDefined();
    expect(screen.getByRole("link", { name: "Gönderdiğim belgeyi aç" }).getAttribute("href")).toBe(
      "/api/seller/application/document",
    );
  });

  it("reddedilen başvuruda gerekçeyi ve dolu yeniden gönderim formunu gösterir", async () => {
    session.current = signedIn(true);
    ownSeller.current = aSeller({ status: "REJECTED", statusReason: "Belgedeki ad okunmuyor." });

    renderWithIntl(await SellerApplicationPage());

    expect(screen.getByRole("heading", { name: "Başvurunuz reddedildi" })).toBeDefined();
    expect(screen.getByText("Belgedeki ad okunmuyor.")).toBeDefined();
    expect(screen.getByLabelText<HTMLInputElement>("Üretici adı").value).toBe("Örnek Bahçe");
    expect(screen.getByRole("button", { name: "Düzeltip yeniden gönder" })).toBeDefined();
  });

  it("onaylı ve askıdaki kayıtta kendi başlığını ve durumunu gösterir", async () => {
    session.current = signedIn(true);
    ownSeller.current = aSeller({ status: "APPROVED" });
    const { unmount } = renderWithIntl(await SellerApplicationPage());
    expect(screen.getByRole("heading", { name: "Başvurunuz onaylandı" })).toBeDefined();
    unmount();

    ownSeller.current = aSeller({ status: "SUSPENDED", statusReason: "Şikâyet inceleniyor." });
    renderWithIntl(await SellerApplicationPage());
    expect(screen.getByRole("heading", { name: "Satıcı kaydınız askıya alındı" })).toBeDefined();
    expect(screen.getByText("Askıya alındı")).toBeDefined();
    expect(screen.getByText("Şikâyet inceleniyor.")).toBeDefined();
  });
});
