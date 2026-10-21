import { fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import { LocaleMenu } from "./LocaleMenu";

/**
 * Dil menüsü (localization spec'i "Kullanıcı dili değiştirir", ADR-030).
 * Yönlendirici ve `fetch` taklit edilir; seçim çereze yazılır, hesaba eşitlenir
 * (add-account-locale-update D2) ve sayfa sunucuda yenilenir.
 */

const router = { refresh: vi.fn() };

vi.mock("next/navigation", () => ({ useRouter: () => router }));

const fetchMock = vi.fn();

beforeEach(() => {
  router.refresh.mockReset();
  fetchMock.mockReset();
  fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  document.cookie = "bahceden_locale=; Path=/; Max-Age=0";
});

describe("LocaleMenu", () => {
  it("tetikleyici seçili dili sayfanın dilinde söyler", () => {
    renderWithIntl(<LocaleMenu />, "en");

    expect(screen.getByRole("button", { name: "Language: English" })).toBeDefined();
  });

  it("seçenekler kendi dilinde yazılır ve seçili dil işaretlidir", async () => {
    renderWithIntl(<LocaleMenu />);

    fireEvent.click(screen.getByRole("button", { name: "Dil: Türkçe" }));

    const options = await screen.findAllByRole("menuitemradio");
    expect(options.map((option) => option.textContent)).toEqual(["Türkçe", "English"]);
    expect(screen.getByRole("menuitemradio", { name: "Türkçe" }).getAttribute("aria-checked")).toBe("true");
  });

  it("başka dil seçilince tercihi çereze yazar ve sayfayı sunucuda yeniler", async () => {
    renderWithIntl(<LocaleMenu />);

    fireEvent.click(screen.getByRole("button", { name: "Dil: Türkçe" }));
    fireEvent.click(await screen.findByRole("menuitemradio", { name: "English" }));

    expect(document.cookie).toContain("bahceden_locale=en");
    await vi.waitFor(() => expect(router.refresh).toHaveBeenCalledTimes(1));
  });

  it("seçimi hesaba eşitler, sonra sayfayı yeniler", async () => {
    renderWithIntl(<LocaleMenu />);

    fireEvent.click(screen.getByRole("button", { name: "Dil: Türkçe" }));
    fireEvent.click(await screen.findByRole("menuitemradio", { name: "English" }));

    await vi.waitFor(() => expect(router.refresh).toHaveBeenCalledTimes(1));
    expect(fetchMock).toHaveBeenCalledOnce();
    const [path, init] = fetchMock.mock.calls[0] ?? [];
    expect(path).toBe("/api/account/locale");
    expect(init).toMatchObject({ method: "PUT", body: JSON.stringify({ locale: "en" }) });
  });

  it("eşitleme başarısız olsa da dil değişir ve sayfa yenilenir", async () => {
    fetchMock.mockRejectedValue(new TypeError("ağ yok"));
    renderWithIntl(<LocaleMenu />);

    fireEvent.click(screen.getByRole("button", { name: "Dil: Türkçe" }));
    fireEvent.click(await screen.findByRole("menuitemradio", { name: "English" }));

    expect(document.cookie).toContain("bahceden_locale=en");
    await vi.waitFor(() => expect(router.refresh).toHaveBeenCalledTimes(1));
  });

  it("seçili dil yeniden seçilirse bir şey yapmaz", async () => {
    renderWithIntl(<LocaleMenu />);

    fireEvent.click(screen.getByRole("button", { name: "Dil: Türkçe" }));
    fireEvent.click(await screen.findByRole("menuitemradio", { name: "Türkçe" }));

    expect(document.cookie).not.toContain("bahceden_locale");
    expect(router.refresh).not.toHaveBeenCalled();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
