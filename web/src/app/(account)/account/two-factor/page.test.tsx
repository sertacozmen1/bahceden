import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { SessionState } from "@/features/account/api";
import TwoFactorPage from "./page";

/**
 * İki adımlı giriş sayfası (add-two-factor-toggle D2). Oturum okuma ve
 * yönlendirici taklit edilir; sunucuya çıkılmaz.
 */

const session = vi.hoisted(() => {
  const state: { current: SessionState } = { current: { kind: "anonymous" } };
  return state;
});

vi.mock("@/features/account/api", () => ({ currentSession: async () => session.current }));
vi.mock("next/navigation", () => ({
  redirect: vi.fn(),
  useRouter: () => ({ push: vi.fn(), refresh: vi.fn() }),
}));

function signedIn(emailVerified: boolean, twoFactorEnabled: boolean): SessionState {
  return {
    kind: "signed-in",
    accessToken: "test-erisim",
    account: { email: "alici@ornek.test", roles: ["ROLE_CUSTOMER"], emailVerified, twoFactorEnabled },
  };
}

beforeEach(() => {
  session.current = { kind: "anonymous" };
});

describe("İki adımlı giriş sayfası", () => {
  it("kapalıyken durumu ve açma formunu gösterir", async () => {
    session.current = signedIn(true, false);

    renderWithIntl(await TwoFactorPage());

    expect(screen.getByText("Kapalı")).toBeDefined();
    expect(screen.getByRole("button", { name: "Aç" })).toBeDefined();
    expect(screen.getByRole("link", { name: "Hesabıma dön" }).getAttribute("href")).toBe("/account");
  });

  it("açıkken durumu ve kapatma düğmesini gösterir", async () => {
    session.current = signedIn(true, true);

    renderWithIntl(await TwoFactorPage());

    expect(screen.getByText("Açık")).toBeDefined();
    expect(screen.getByRole("button", { name: "Kapat" })).toBeDefined();
  });

  it("e-posta doğrulanmamış ve kapalıysa form yerine doğrulama yönlendirmesi gösterir", async () => {
    session.current = signedIn(false, false);

    renderWithIntl(await TwoFactorPage());

    expect(screen.getByRole("heading", { name: "Önce e-posta adresinizi doğrulayın" })).toBeDefined();
    expect(screen.getByRole("button", { name: "Yeni doğrulama bağlantısı gönder" })).toBeDefined();
    expect(screen.queryByLabelText("Mevcut parola")).toBeNull();
  });

  it("e-posta doğrulanmamış ama açıksa kapatma formu gösterir: kapatmak doğrulama istemez", async () => {
    session.current = signedIn(false, true);

    renderWithIntl(await TwoFactorPage());

    expect(screen.getByRole("button", { name: "Kapat" })).toBeDefined();
  });
});
