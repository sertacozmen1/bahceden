import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import SessionRefreshPage from "./page";

/**
 * Oturum yenileme ara sayfası (ADR-023): yenileme ucuna tam sayfa gezintisiyle
 * geçer; dönüş yolu yalnızca site içi olabilir.
 */

const replace = vi.fn();

afterEach(() => {
  replace.mockReset();
  vi.unstubAllGlobals();
});

function stubLocation() {
  vi.stubGlobal("location", { ...window.location, replace });
}

describe("Oturum yenileme ara sayfası", () => {
  it("yenileme ucuna tam sayfa gezintisiyle gider ve dönüş yolunu taşır", async () => {
    stubLocation();

    renderWithIntl(await SessionRefreshPage({ searchParams: Promise.resolve({ next: "/seller/products" }) }));

    expect(replace).toHaveBeenCalledWith("/api/auth/refresh?next=%2Fseller%2Fproducts");
    expect(screen.getByRole("link", { name: "Devam et" }).getAttribute("href")).toBe(
      "/api/auth/refresh?next=%2Fseller%2Fproducts",
    );
  });

  it("site dışı dönüş yolunu kabul etmez; hesap sayfasına döner", async () => {
    stubLocation();

    renderWithIntl(await SessionRefreshPage({ searchParams: Promise.resolve({ next: "//saldirgan.test" }) }));

    expect(replace).toHaveBeenCalledWith("/api/auth/refresh?next=%2Faccount");
  });
});
