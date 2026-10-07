import { fireEvent, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { renderWithIntl } from "@/test/intl";
import { parseTheme } from "@/lib/theme";
import { ThemeMenu } from "./ThemeMenu";

/**
 * Tema menüsü (ADR-028). Davranış testi: tetikleyici ve seçenekler
 * erişilebilir adlarıyla sorgulanır; seçim `<html data-theme>`'ya ve çereze
 * yansır.
 */

afterEach(() => {
  delete document.documentElement.dataset.theme;
  document.cookie = "bahceden_theme=; Path=/; Max-Age=0";
});

describe("ThemeMenu", () => {
  it("tek ikonlu tetikleyici seçili temayı adıyla söyler", () => {
    renderWithIntl(<ThemeMenu initialTheme="system" />);

    expect(screen.getByRole("button", { name: "Tema: Sistem" })).toBeDefined();
    expect(screen.queryByRole("menu")).toBeNull();
  });

  it("açılınca üç seçenek sunar ve seçili olanı işaretler", async () => {
    renderWithIntl(<ThemeMenu initialTheme="light" />);

    fireEvent.click(screen.getByRole("button", { name: "Tema: Açık" }));

    const options = await screen.findAllByRole("menuitemradio");
    expect(options.map((option) => option.textContent)).toEqual(["Açık", "Koyu", "Sistem"]);
    expect(screen.getByRole("menuitemradio", { name: "Açık" }).getAttribute("aria-checked")).toBe("true");
  });

  it("seçim temayı hemen değiştirir, çereze yazar ve tetikleyiciyi günceller", async () => {
    renderWithIntl(<ThemeMenu initialTheme="system" />);

    fireEvent.click(screen.getByRole("button", { name: "Tema: Sistem" }));
    fireEvent.click(await screen.findByRole("menuitemradio", { name: "Koyu" }));

    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(document.cookie).toContain("bahceden_theme=dark");
    expect(screen.getByRole("button", { name: "Tema: Koyu" })).toBeDefined();
  });
});

describe("parseTheme", () => {
  it("tanınan değeri olduğu gibi döner", () => {
    expect(parseTheme("light")).toBe("light");
    expect(parseTheme("dark")).toBe("dark");
  });

  it("çerez yoksa veya değeri tanınmıyorsa sisteme düşer", () => {
    expect(parseTheme(undefined)).toBe("system");
    expect(parseTheme("mor")).toBe("system");
  });
});
