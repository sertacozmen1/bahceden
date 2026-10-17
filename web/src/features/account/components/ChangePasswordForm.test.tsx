import { fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import { ChangePasswordForm } from "./ChangePasswordForm";

/**
 * Oturum açıkken parola değiştirme formu (account-identity spec'i "Oturum
 * açıkken parola değiştirme", add-password-change D4). Ağ ve yönlendirici
 * taklit edilir (AGENTS.md §12.6).
 */

const router = { push: vi.fn(), refresh: vi.fn() };

vi.mock("next/navigation", () => ({ useRouter: () => router }));

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function fillAndSubmit(currentPassword: string, newPassword: string) {
  fireEvent.change(screen.getByLabelText("Mevcut parola"), { target: { value: currentPassword } });
  fireEvent.change(screen.getByLabelText("Yeni parola"), { target: { value: newPassword } });
  fireEvent.click(screen.getByRole("button", { name: "Parolayı değiştir" }));
}

beforeEach(() => {
  router.push.mockReset();
  router.refresh.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ChangePasswordForm", () => {
  it("yanlış mevcut parola hatasını o alanın altına bağlar ve yönlendirmez", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(400, {
          code: "VALIDATION_FAILED",
          message: "Gönderilen değerler geçersiz.",
          fieldErrors: [{ field: "currentPassword", message: "Mevcut parola doğru değil" }],
        }),
      ),
    );
    renderWithIntl(<ChangePasswordForm />);

    fillAndSubmit("yanlis-gizli-parola", "yepyeni-gizli-parola");

    expect(await screen.findByText("Mevcut parola doğru değil")).toBeDefined();
    expect(screen.getByLabelText("Mevcut parola").getAttribute("aria-invalid")).toBe("true");
    expect(screen.getByLabelText("Yeni parola").getAttribute("aria-invalid")).not.toBe("true");
    expect(router.push).not.toHaveBeenCalled();
  });

  it("kurala uymayan yeni parola hatasını yeni parola alanına bağlar", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(400, {
          code: "VALIDATION_FAILED",
          message: "Gönderilen değerler geçersiz.",
          fieldErrors: [{ field: "newPassword", message: "Parola en az 10, en çok 200 karakter olmalıdır" }],
        }),
      ),
    );
    renderWithIntl(<ChangePasswordForm />);

    fillAndSubmit("eski-gizli-parola", "kisa");

    expect(await screen.findByText("Parola en az 10, en çok 200 karakter olmalıdır")).toBeDefined();
    expect(screen.getByLabelText("Yeni parola").getAttribute("aria-invalid")).toBe("true");
    expect(router.push).not.toHaveBeenCalled();
  });

  it("hız sınırında sunucunun mesajını formun üstünde gösterir", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(429, { code: "RATE_LIMITED", message: "Çok fazla deneme yaptınız.", fieldErrors: [] }),
      ),
    );
    renderWithIntl(<ChangePasswordForm />);

    fillAndSubmit("eski-gizli-parola", "yepyeni-gizli-parola");

    expect((await screen.findByRole("alert")).textContent).toBe("Çok fazla deneme yaptınız.");
    expect(router.push).not.toHaveBeenCalled();
  });

  it("başarıda iki parolayı gönderir ve giriş ekranına bilgiyle yönlendirir", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<ChangePasswordForm />);

    fillAndSubmit("eski-gizli-parola", "yepyeni-gizli-parola");

    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith("/login?reset=1"));
    expect(router.refresh).toHaveBeenCalled();
    const [path, init] = fetchMock.mock.calls[0] ?? [];
    expect(path).toBe("/api/auth/password-change");
    expect(JSON.parse(String(init?.body))).toEqual({
      currentPassword: "eski-gizli-parola",
      newPassword: "yepyeni-gizli-parola",
    });
  });
});
