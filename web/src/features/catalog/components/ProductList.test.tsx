import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { ProductSummary } from "@/features/catalog/api";
import { ProductList } from "./ProductList";

/**
 * Vitrin listesinin davranışı (storefront-catalog spec'i, ADR-005).
 *
 * Davranış testi: kullanıcının gördüğü rol ve metinlerle sorgulanır, snapshot
 * alınmaz. Veri sahtedir ve ağa çıkılmaz (K10).
 */

function aProduct(overrides: Partial<ProductSummary> = {}): ProductSummary {
  return {
    slug: "sultani-uzum",
    name: "Sultani Üzüm",
    unit: "KILOGRAM",
    unitPrice: { amount: 4250, currency: "TRY" },
    stockQuantity: 120,
    seller: {
      slug: "ornek-uretici-manisa",
      displayName: "Örnek Üretici (Manisa)",
      city: "Manisa",
    },
    ...overrides,
  };
}

describe("ProductList", () => {
  it("ürünü adı, üreticisi ve fiyatıyla gösterir", () => {
    renderWithIntl(<ProductList products={[aProduct()]} />);

    expect(screen.getByRole("heading", { name: "Sultani Üzüm" })).toBeDefined();
    expect(screen.getByText("Örnek Üretici (Manisa)")).toBeDefined();
    expect(screen.getByText("Manisa üreticisi")).toBeDefined();
    // Kuruş biçimlendirmesi tek yardımcıda (ADR-006): 4250 → 42,50 TL
    expect(screen.getByText(/42,50/)).toBeDefined();
    expect(screen.getByText("/ kg")).toBeDefined();
  });

  it("boş listede yönlendirici boş durum gösterir, boş sayfa bırakmaz", () => {
    renderWithIntl(<ProductList products={[]} />);

    expect(screen.getByRole("heading", { name: "Vitrinde henüz ürün yok" })).toBeDefined();
    expect(screen.getByText("Üreticiler ilk ürünlerini eklediğinde burada görünecek.")).toBeDefined();
    expect(screen.queryByRole("list")).toBeNull();
  });

  it("stok bittiğinde durumu metinle söyler", () => {
    renderWithIntl(<ProductList products={[aProduct({ stockQuantity: 0 })]} />);

    expect(screen.getByText("Stokta yok")).toBeDefined();
  });

  it("stok azaldığında kalan adedi söyler", () => {
    renderWithIntl(<ProductList products={[aProduct({ stockQuantity: 3 })]} />);

    expect(screen.getByText("Son 3 adet")).toBeDefined();
  });

  it("birime göre doğru etiketi kullanır", () => {
    renderWithIntl(
      <ProductList
        products={[
          aProduct({ slug: "avokado", unit: "PIECE" }),
          aProduct({ slug: "maydanoz", unit: "BUNCH" }),
        ]}
      />,
    );

    expect(screen.getByText("/ adet")).toBeDefined();
    expect(screen.getByText("/ demet")).toBeDefined();
  });

  it("her ürün için bir liste öğesi üretir", () => {
    renderWithIntl(<ProductList products={[aProduct({ slug: "a" }), aProduct({ slug: "b" })]} />);

    expect(screen.getAllByRole("listitem")).toHaveLength(2);
  });

  it("İngilizcede metinleri ve para biçimini çevirir; ürün ve üretici adı olduğu gibi kalır (ADR-029)", () => {
    renderWithIntl(<ProductList products={[aProduct({ stockQuantity: 3 })]} />, "en");

    expect(screen.getByRole("heading", { name: "Sultani Üzüm" })).toBeDefined();
    expect(screen.getByText("Örnek Üretici (Manisa)")).toBeDefined();
    expect(screen.getByText("Grower from Manisa")).toBeDefined();
    expect(screen.getByText("Only 3 left")).toBeDefined();
    expect(screen.getByText("/ kg")).toBeDefined();
    // İngilizcede ondalık ayırıcı nokta; simge her dilde ₺ (lib/money.ts).
    expect(screen.getByText("₺42.50")).toBeDefined();
  });
  it("kapak fotoğrafını ürün adıyla gösterir ve detay sayfasına bağlar", () => {
    renderWithIntl(
      <ProductList
        products={[aProduct({ coverPhoto: { id: 3, cardKey: "a".repeat(32), largeKey: "b".repeat(32), width: 1600, height: 1200 } })]}
      />,
    );

    const image = screen.getByRole("img", { name: "Sultani Üzüm" });
    expect(image.getAttribute("src")).toBe(`/media/product-photos/${"a".repeat(32)}`);
    expect(image.getAttribute("width")).toBe("400");
    expect(screen.getByRole("link", { name: "Sultani Üzüm" }).getAttribute("href")).toBe("/products/sultani-uzum");
  });

  it("fotoğrafı olmayan üründe yer tutucu görsel çizmez", () => {
    renderWithIntl(<ProductList products={[aProduct()]} />);

    expect(screen.queryByRole("img")).toBeNull();
  });
});
