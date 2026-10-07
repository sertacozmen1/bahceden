import { fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import { ForgotPasswordForm } from "./ForgotPasswordForm";

/**
 * Parola sıfırlama talebi formu (account-identity spec'i "Parola sıfırlama").
 * Ağ taklit edilir; test hiçbir sunucuya çıkmaz (AGENTS.md §12.6, K10).
 */

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function fillAndSubmit(email: string) {
  fireEvent.change(screen.getByLabelText("E-posta adresi"), { target: { value: email } });
  fireEvent.click(screen.getByRole("button", { name: "Bağlantı gönder" }));
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ForgotPasswordForm", () => {
  it("sunucunun alan hatasını e-posta alanının altına bağlar", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(400, {
          code: "VALIDATION_FAILED",
          message: "Gönderilen değerler geçersiz.",
          fieldErrors: [{ field: "email", message: "Geçerli bir e-posta adresi girin" }],
        }),
      ),
    );
    renderWithIntl(<ForgotPasswordForm />);

    fillAndSubmit("eposta-degil");

    expect(await screen.findByText("Geçerli bir e-posta adresi girin")).toBeDefined();
    expect(screen.getByLabelText("E-posta adresi").getAttribute("aria-invalid")).toBe("true");
  });

  it("hız sınırında sunucunun genel mesajını gösterir", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(429, {
          code: "RATE_LIMITED",
          message: "Çok fazla deneme yaptınız. Lütfen biraz sonra tekrar deneyin.",
          fieldErrors: [],
        }),
      ),
    );
    renderWithIntl(<ForgotPasswordForm />);

    fillAndSubmit("alici@ornek.test");

    expect((await screen.findByRole("alert")).textContent).toBe(
      "Çok fazla deneme yaptınız. Lütfen biraz sonra tekrar deneyin.",
    );
  });

  it("başarıda sunucunun mesajını ve giriş bağlantısını gösterir; e-postayı kırparak gönderir", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(202, { message: "Bu adrese kayıtlı bir hesap varsa parola sıfırlama bağlantısı gönderdik." }),
    );
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<ForgotPasswordForm />);

    fillAndSubmit("  alici@ornek.test  ");

    expect(
      await screen.findByText("Bu adrese kayıtlı bir hesap varsa parola sıfırlama bağlantısı gönderdik."),
    ).toBeDefined();
    expect(screen.getByRole("link", { name: "Giriş ekranına dön" }).getAttribute("href")).toBe("/login");
    const [path, init] = fetchMock.mock.calls[0] ?? [];
    expect(path).toBe("/api/auth/password-reset-request");
    expect(JSON.parse(String(init?.body))).toEqual({ email: "alici@ornek.test" });
  });
});
