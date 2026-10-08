import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { SessionState } from "@/features/account/api";
import MyAccountPage from "./page";

/**
 * Hesabım sayfasının yetkisiz durumu (AGENTS.md §12.5, design.md D6).
 * Oturum okuma ve yönlendirme taklit edilir; sunucuya çıkılmaz.
 */

const session = vi.hoisted(() => {
  const state: { current: SessionState } = { current: { kind: "anonymous" } };
  return state;
});

vi.mock("@/features/account/api", () => ({ currentSession: async () => session.current }));

const ownSeller = vi.hoisted(() => ({ current: null as null | { status: "PENDING" } }));
vi.mock("@/features/seller/api", () => ({ getOwnSeller: async () => ownSeller.current }));

const redirect = vi.hoisted(() =>
  vi.fn((target: string) => {
    throw new Error(`yönlendirildi: ${target}`);
  }),
);
vi.mock("next/navigation", () => ({ redirect, useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));

beforeEach(() => {
  redirect.mockClear();
  ownSeller.current = null;
});

describe("Hesabım sayfası", () => {
  it("oturum yoksa giriş sayfasına, dönüş yoluyla yönlendirir", async () => {
    session.current = { kind: "anonymous" };

    await expect(MyAccountPage()).rejects.toThrow("yönlendirildi");
    expect(redirect).toHaveBeenCalledWith("/login?next=%2Faccount");
  });

  it("erişim token'ının süresi dolduysa yenileme ara sayfasına yönlendirir; Route Handler'a değil (uygulama içi gezinti onu izleyemez)", async () => {
    session.current = { kind: "expired" };

    await expect(MyAccountPage()).rejects.toThrow("yönlendirildi");
    expect(redirect).toHaveBeenCalledWith("/session/refresh?next=%2Faccount");
  });

  it("oturum açıksa hesap bilgisini ve doğrulama durumunu gösterir", async () => {
    session.current = {
      kind: "signed-in",
      accessToken: "test-erisim",
      account: { email: "alici@ornek.test", roles: ["ROLE_CUSTOMER"], emailVerified: false },
    };

    renderWithIntl(await MyAccountPage());

    expect(screen.getByText("alici@ornek.test")).toBeDefined();
    expect(screen.getByText("Alıcı")).toBeDefined();
    expect(screen.getByText("Doğrulanmadı")).toBeDefined();
    expect(screen.getByRole("button", { name: "Yeni doğrulama bağlantısı gönder" })).toBeDefined();
    expect(redirect).not.toHaveBeenCalled();
  });

  it("satıcı kaydı yoksa başvuruya davet eder", async () => {
    session.current = {
      kind: "signed-in",
      accessToken: "test-erisim",
      account: { email: "alici@ornek.test", roles: ["ROLE_CUSTOMER"], emailVerified: true },
    };

    renderWithIntl(await MyAccountPage());

    expect(screen.getByRole("heading", { name: "Satıcılık" })).toBeDefined();
    expect(screen.getByRole("link", { name: "Satıcı olarak başvur" }).getAttribute("href")).toBe("/seller/application");
  });

  it("satıcı kaydı varsa durumunu ve ayrıntı bağlantısını gösterir", async () => {
    session.current = {
      kind: "signed-in",
      accessToken: "test-erisim",
      account: { email: "alici@ornek.test", roles: ["ROLE_CUSTOMER"], emailVerified: true },
    };
    ownSeller.current = { status: "PENDING" };

    renderWithIntl(await MyAccountPage());

    expect(screen.getByText("İnceleniyor")).toBeDefined();
    expect(screen.getByRole("link", { name: "Başvuru ayrıntıları" })).toBeDefined();
  });
});
