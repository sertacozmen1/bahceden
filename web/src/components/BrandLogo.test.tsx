import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BrandLogo, BrandMark } from "./BrandLogo";

/**
 * Logo süsleme değil, ad taşır: ekran okuyucu onu verilen adla okur ve
 * renkler sabit değer değil, temaya bağlı `logo-*` rollerinden gelir.
 */
describe("BrandLogo", () => {
  it("logo ve dar alan işareti verilen adla okunur", () => {
    render(
      <>
        <BrandLogo label="Bahçeden" />
        <BrandMark label="Bahçeden" />
      </>,
    );
    expect(screen.getAllByRole("img", { name: "Bahçeden" })).toHaveLength(2);
  });

  it("kelime işareti rengini temaya bağlı rolden alır", () => {
    render(<BrandLogo label="Bahçeden" />);
    const svg = screen.getByRole("img", { name: "Bahçeden" });
    expect(svg.querySelector(".fill-logo-wordmark")).not.toBeNull();
    expect(svg.querySelector("[fill^='#']")).toBeNull();
  });
});
