import { fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import { LoginForm } from "./LoginForm";

/**
 * Giriş formu (account-identity spec'i "Oturum açma" ve "E-posta ile ikinci
 * faktör"). Ağ ve yönlendirici taklit edilir (AGENTS.md §12.6).
 */

const router = { push: vi.fn(), refresh: vi.fn() };

vi.mock("next/navigation", () => ({ useRouter: () => router }));

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function kimlikGonder() {
  fireEvent.change(screen.getByLabelText("E-posta adresi"), { target: { value: "alici@ornek.test" } });
  fireEvent.change(screen.getByLabelText("Parola"), { target: { value: "cok-gizli-parola" } });
  fireEvent.click(screen.getByRole("button", { name: "Giriş yap" }));
}

beforeEach(() => {
  router.push.mockReset();
  router.refresh.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("LoginForm", () => {
  it("hatalı kimlik bilgisinde sunucunun genel mesajını gösterir, alan işaretlemez", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(401, {
          code: "AUTH_INVALID_CREDENTIALS",
          message: "E-posta adresi veya parola hatalı.",
          fieldErrors: [],
        }),
      ),
    );
    renderWithIntl(<LoginForm returnPath="/hesabim" />);

    kimlikGonder();

    expect((await screen.findByRole("alert")).textContent).toBe("E-posta adresi veya parola hatalı.");
    expect(screen.getByLabelText("E-posta adresi").getAttribute("aria-invalid")).toBe("false");
    expect(screen.getByLabelText("Parola").getAttribute("aria-invalid")).toBe("false");
    expect(router.push).not.toHaveBeenCalled();
  });

  it("başarıda hedefe gider ve sunucu bileşenlerini yeniler", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(200, { outcome: "AUTHENTICATED" })));
    renderWithIntl(<LoginForm returnPath="/hesabim" />);

    kimlikGonder();

    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith("/hesabim"));
    expect(router.refresh).toHaveBeenCalled();
  });

  it("ikinci adım beklenince aynı ekranda kod alanı açılır ve kod e-postayla birlikte gönderilir", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse(200, { outcome: "CHALLENGE_REQUIRED", message: "E-posta adresinize bir giriş kodu gönderdik." }),
      )
      .mockResolvedValueOnce(jsonResponse(200, { outcome: "AUTHENTICATED" }));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<LoginForm returnPath="/hesabim" />);

    kimlikGonder();

    expect(await screen.findByText("E-posta adresinize bir giriş kodu gönderdik.")).toBeDefined();
    expect(screen.queryByLabelText("Parola")).toBeNull();

    fireEvent.change(screen.getByLabelText("Giriş kodu"), { target: { value: " 042917 " } });
    fireEvent.click(screen.getByRole("button", { name: "Kodu onayla" }));

    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith("/hesabim"));
    const [path, init] = fetchMock.mock.calls[1] ?? [];
    expect(path).toBe("/api/auth/challenge");
    expect(JSON.parse(String(init?.body))).toEqual({ email: "alici@ornek.test", code: "042917" });
  });

  it("hatalı kod sunucu mesajıyla bildirilir ve kod adımı açık kalır", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(jsonResponse(200, { outcome: "CHALLENGE_REQUIRED", message: "Kod gönderildi." }))
        .mockResolvedValueOnce(
          jsonResponse(401, { code: "AUTH_CODE_INVALID", message: "Kod hatalı ya da süresi dolmuş.", fieldErrors: [] }),
        ),
    );
    renderWithIntl(<LoginForm returnPath="/hesabim" />);

    kimlikGonder();
    fireEvent.change(await screen.findByLabelText("Giriş kodu"), { target: { value: "000000" } });
    fireEvent.click(screen.getByRole("button", { name: "Kodu onayla" }));

    expect((await screen.findByRole("alert")).textContent).toBe("Kod hatalı ya da süresi dolmuş.");
    expect(screen.getByLabelText("Giriş kodu")).toBeDefined();
  });
});
