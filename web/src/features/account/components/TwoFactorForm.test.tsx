import { fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import { TwoFactorForm } from "./TwoFactorForm";

/**
 * İki adımlı giriş formu (account-identity spec'i "E-posta ile ikinci faktör,
 * hesap bazlı anahtarla", add-two-factor-toggle D2). Ağ ve yönlendirici taklit
 * edilir (AGENTS.md §12.6).
 */

const router = { push: vi.fn(), refresh: vi.fn() };

vi.mock("next/navigation", () => ({ useRouter: () => router }));

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function submit(button: string, currentPassword = "cok-gizli-parola") {
  fireEvent.change(screen.getByLabelText("Mevcut parola"), { target: { value: currentPassword } });
  fireEvent.click(screen.getByRole("button", { name: button }));
}

beforeEach(() => {
  router.push.mockReset();
  router.refresh.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("TwoFactorForm", () => {
  it("kapalıyken açmayı ister; başarıda sonucu söyler, sayfayı yeniler, oturumu kapatmaz", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<TwoFactorForm enabled={false} />);

    submit("Aç");

    expect(await screen.findByText(/İki adımlı giriş açıldı/)).toBeDefined();
    const [path, init] = fetchMock.mock.calls[0] ?? [];
    expect(path).toBe("/api/account/two-factor");
    expect(init).toMatchObject({
      method: "PUT",
      body: JSON.stringify({ enabled: true, currentPassword: "cok-gizli-parola" }),
    });
    expect(router.refresh).toHaveBeenCalledOnce();
    expect(router.push).not.toHaveBeenCalled();
  });

  it("açıkken kapatmayı ister", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<TwoFactorForm enabled />);

    submit("Kapat");

    expect(await screen.findByText(/İki adımlı giriş kapatıldı/)).toBeDefined();
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      body: JSON.stringify({ enabled: false, currentPassword: "cok-gizli-parola" }),
    });
  });

  it("yanlış mevcut parola hatasını alana bağlar ve sayfayı yenilemez", async () => {
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
    renderWithIntl(<TwoFactorForm enabled={false} />);

    submit("Aç", "yanlis-gizli-parola");

    expect(await screen.findByText("Mevcut parola doğru değil")).toBeDefined();
    expect(screen.getByLabelText("Mevcut parola").getAttribute("aria-invalid")).toBe("true");
    expect(router.refresh).not.toHaveBeenCalled();
  });

  it("doğrulanmamış e-posta hatasında sunucunun mesajını formun üstünde gösterir", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(422, {
          code: "TWO_FACTOR_EMAIL_NOT_VERIFIED",
          message: "İki adımlı girişi açmadan önce e-posta adresinizi doğrulayın.",
          fieldErrors: [],
        }),
      ),
    );
    renderWithIntl(<TwoFactorForm enabled={false} />);

    submit("Aç");

    expect(await screen.findByText("İki adımlı girişi açmadan önce e-posta adresinizi doğrulayın.")).toBeDefined();
    expect(router.refresh).not.toHaveBeenCalled();
  });
});
