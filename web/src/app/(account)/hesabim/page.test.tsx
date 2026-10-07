import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
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

const redirect = vi.hoisted(() =>
  vi.fn((target: string) => {
    throw new Error(`yönlendirildi: ${target}`);
  }),
);
vi.mock("next/navigation", () => ({ redirect, useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }) }));

beforeEach(() => {
  redirect.mockClear();
});

describe("Hesabım sayfası", () => {
  it("oturum yoksa giriş sayfasına, dönüş yoluyla yönlendirir", async () => {
    session.current = { kind: "anonymous" };

    await expect(MyAccountPage()).rejects.toThrow("yönlendirildi");
    expect(redirect).toHaveBeenCalledWith("/giris?next=%2Fhesabim");
  });

  it("erişim token'ının süresi dolduysa önce yenilemeye yönlendirir", async () => {
    session.current = { kind: "expired" };

    await expect(MyAccountPage()).rejects.toThrow("yönlendirildi");
    expect(redirect).toHaveBeenCalledWith("/api/auth/refresh?next=%2Fhesabim");
  });

  it("oturum açıksa hesap bilgisini ve doğrulama durumunu gösterir", async () => {
    session.current = {
      kind: "signed-in",
      accessToken: "test-erisim",
      account: { email: "alici@ornek.test", roles: ["ROLE_CUSTOMER"], emailVerified: false },
    };

    render(await MyAccountPage());

    expect(screen.getByText("alici@ornek.test")).toBeDefined();
    expect(screen.getByText("Alıcı")).toBeDefined();
    expect(screen.getByText("Doğrulanmadı")).toBeDefined();
    expect(screen.getByRole("button", { name: "Yeni doğrulama bağlantısı gönder" })).toBeDefined();
    expect(redirect).not.toHaveBeenCalled();
  });
});
