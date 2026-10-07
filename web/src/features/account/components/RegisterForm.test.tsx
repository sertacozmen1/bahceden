import { fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import { RegisterForm } from "./RegisterForm";

/**
 * Kayıt formu (account-identity spec'i "E-posta ve parola ile hesap açma").
 * Ağ taklit edilir; test hiçbir sunucuya çıkmaz (AGENTS.md §12.6, K10).
 */

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function fillAndSubmit(email: string, password: string) {
  fireEvent.change(screen.getByLabelText("E-posta adresi"), { target: { value: email } });
  fireEvent.change(screen.getByLabelText("Parola"), { target: { value: password } });
  fireEvent.click(screen.getByRole("button", { name: "Hesap aç" }));
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("RegisterForm", () => {
  it("sunucunun alan hatasını ilgili alanın altına bağlar", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(400, {
          code: "VALIDATION_FAILED",
          message: "Gönderilen değerler geçersiz.",
          fieldErrors: [{ field: "password", message: "Parola en az 10, en çok 200 karakter olmalıdır" }],
        }),
      ),
    );
    renderWithIntl(<RegisterForm />);

    fillAndSubmit("alici@ornek.test", "kisa");

    expect(await screen.findByText("Parola en az 10, en çok 200 karakter olmalıdır")).toBeDefined();
    expect(screen.getByLabelText("Parola").getAttribute("aria-invalid")).toBe("true");
    expect(screen.getByRole("alert").textContent).toBe("Formdaki işaretli alanları düzeltin.");
  });

  it("sunucuya ulaşılamazsa genel bir hata gösterir", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("ağ yok")));
    renderWithIntl(<RegisterForm />);

    fillAndSubmit("alici@ornek.test", "cok-gizli-parola");

    expect((await screen.findByRole("alert")).textContent).toBe(
      "Sunucuya ulaşamadık. Bağlantınızı kontrol edip yeniden deneyin.",
    );
  });

  it("başarıda sunucunun mesajını ve giriş bağlantısını gösterir; e-postayı kırparak gönderir", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(202, { message: "E-posta adresinize bir bağlantı gönderdik." }),
    );
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<RegisterForm />);

    fillAndSubmit("  alici@ornek.test  ", "cok-gizli-parola");

    expect(await screen.findByText("E-posta adresinize bir bağlantı gönderdik.")).toBeDefined();
    expect(screen.getByRole("link", { name: "Giriş yap" }).getAttribute("href")).toBe("/login");
    const [path, init] = fetchMock.mock.calls[0] ?? [];
    expect(path).toBe("/api/auth/register");
    expect(JSON.parse(String(init?.body))).toEqual({ email: "alici@ornek.test", password: "cok-gizli-parola" });
  });
});
