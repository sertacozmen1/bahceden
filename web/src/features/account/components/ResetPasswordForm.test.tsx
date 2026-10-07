import { fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import { ResetPasswordForm } from "./ResetPasswordForm";

/**
 * Yeni parola formu (account-identity spec'i "Parola sıfırlama", ADR-035).
 * Ağ ve yönlendirici taklit edilir (AGENTS.md §12.6).
 */

const router = { push: vi.fn() };

vi.mock("next/navigation", () => ({ useRouter: () => router }));

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function fillAndSubmit(password: string) {
  fireEvent.change(screen.getByLabelText("Yeni parola"), { target: { value: password } });
  fireEvent.click(screen.getByRole("button", { name: "Parolayı değiştir" }));
}

beforeEach(() => {
  router.push.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ResetPasswordForm", () => {
  it("sunucunun parola hatasını alanın altına bağlar ve yönlendirmez", async () => {
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
    renderWithIntl(<ResetPasswordForm token="ornek-token" />);

    fillAndSubmit("kisa");

    expect(await screen.findByText("Parola en az 10, en çok 200 karakter olmalıdır")).toBeDefined();
    expect(screen.getByLabelText("Yeni parola").getAttribute("aria-invalid")).toBe("true");
    expect(router.push).not.toHaveBeenCalled();
  });

  it("geçersiz bağlantıda sunucunun mesajını formun üstünde gösterir", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(400, {
          code: "AUTH_TOKEN_INVALID",
          message: "Parola sıfırlama bağlantısı geçersiz, daha önce kullanılmış ya da süresi dolmuş.",
          fieldErrors: [],
        }),
      ),
    );
    renderWithIntl(<ResetPasswordForm token="kullanilmis-token" />);

    fillAndSubmit("yepyeni-gizli-parola");

    expect((await screen.findByRole("alert")).textContent).toBe(
      "Parola sıfırlama bağlantısı geçersiz, daha önce kullanılmış ya da süresi dolmuş.",
    );
    expect(router.push).not.toHaveBeenCalled();
  });

  it("başarıda token ve parolayı gönderir, giriş ekranına bilgiyle yönlendirir", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<ResetPasswordForm token="ornek-token" />);

    fillAndSubmit("yepyeni-gizli-parola");

    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith("/login?reset=1"));
    const [path, init] = fetchMock.mock.calls[0] ?? [];
    expect(path).toBe("/api/auth/password-reset");
    expect(JSON.parse(String(init?.body))).toEqual({ token: "ornek-token", password: "yepyeni-gizli-parola" });
  });
});
