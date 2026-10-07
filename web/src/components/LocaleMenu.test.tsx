import { fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import { LocaleMenu } from "./LocaleMenu";

/**
 * Dil menüsü (localization spec'i "Kullanıcı dili değiştirir", ADR-030).
 * Yönlendirici taklit edilir; seçim çereze yazılır ve sayfa sunucuda yenilenir.
 */

const router = { refresh: vi.fn() };

vi.mock("next/navigation", () => ({ useRouter: () => router }));

beforeEach(() => {
  router.refresh.mockReset();
});

afterEach(() => {
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

  it("seçili dil yeniden seçilirse bir şey yapmaz", async () => {
    renderWithIntl(<LocaleMenu />);

    fireEvent.click(screen.getByRole("button", { name: "Dil: Türkçe" }));
    fireEvent.click(await screen.findByRole("menuitemradio", { name: "Türkçe" }));

    expect(document.cookie).not.toContain("bahceden_locale");
    expect(router.refresh).not.toHaveBeenCalled();
  });
});
