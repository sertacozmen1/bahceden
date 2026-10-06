import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { ProductSummary } from "@/features/catalog/api";
import { ProductList } from "./ProductList";

/**
 * Vitrin listesinin davranışı (storefront-catalog spec'i, ADR-005).
 *
 * Davranış testi: kullanıcının gördüğü rol ve metinlerle sorgulanır, snapshot
 * alınmaz. Veri sahtedir ve ağa çıkılmaz (K10).
 */

function urun(overrides: Partial<ProductSummary> = {}): ProductSummary {
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
    render(<ProductList products={[urun()]} />);

    expect(screen.getByRole("heading", { name: "Sultani Üzüm" })).toBeDefined();
    expect(screen.getByText("Örnek Üretici (Manisa)")).toBeDefined();
    expect(screen.getByText("Manisa üreticisi")).toBeDefined();
    // Kuruş biçimlendirmesi tek yardımcıda (ADR-006): 4250 → 42,50 TL
    expect(screen.getByText(/42,50/)).toBeDefined();
    expect(screen.getByText("/ kg")).toBeDefined();
  });

  it("boş listede yönlendirici boş durum gösterir, boş sayfa bırakmaz", () => {
    render(<ProductList products={[]} />);

    expect(screen.getByRole("heading", { name: "Vitrinde henüz ürün yok" })).toBeDefined();
    expect(screen.getByText("Üreticiler ilk ürünlerini eklediğinde burada görünecek.")).toBeDefined();
    expect(screen.queryByRole("list")).toBeNull();
  });

  it("stok bittiğinde durumu metinle söyler", () => {
    render(<ProductList products={[urun({ stockQuantity: 0 })]} />);

    expect(screen.getByText("Stokta yok")).toBeDefined();
  });

  it("stok azaldığında kalan adedi söyler", () => {
    render(<ProductList products={[urun({ stockQuantity: 3 })]} />);

    expect(screen.getByText("Son 3 adet")).toBeDefined();
  });

  it("birime göre doğru etiketi kullanır", () => {
    render(
      <ProductList
        products={[
          urun({ slug: "avokado", unit: "PIECE" }),
          urun({ slug: "maydanoz", unit: "BUNCH" }),
        ]}
      />,
    );

    expect(screen.getByText("/ adet")).toBeDefined();
    expect(screen.getByText("/ demet")).toBeDefined();
  });

  it("her ürün için bir liste öğesi üretir", () => {
    render(<ProductList products={[urun({ slug: "a" }), urun({ slug: "b" })]} />);

    expect(screen.getAllByRole("listitem")).toHaveLength(2);
  });
});
