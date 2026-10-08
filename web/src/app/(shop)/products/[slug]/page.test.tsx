import { fireEvent, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { ProductDetail } from "@/features/catalog/api";
import ProductPage from "./page";

/**
 * Ürün detayı sayfası (storefront-catalog "Ürün detayı"). Veri taklit edilir;
 * sunucuya çıkılmaz.
 */

const fetchProduct = vi.hoisted(() => vi.fn());
vi.mock("@/features/catalog/api", () => ({ fetchProduct }));

const notFound = vi.hoisted(() =>
  vi.fn(() => {
    throw new Error("bulunamadı");
  }),
);
vi.mock("next/navigation", () => ({ notFound }));

function aPhoto(id: number) {
  return { id, cardKey: String(id).padStart(32, "c"), largeKey: String(id).padStart(32, "l"), width: 1600, height: 1200 };
}

const product: ProductDetail = {
  slug: "dut",
  name: "Dut",
  description: "Ağaçtan yeni toplandı.",
  unit: "KILOGRAM",
  unitPrice: { amount: 9900, currency: "TRY" },
  stockQuantity: 4,
  seller: { slug: "uretici", displayName: "Üretici Bahçesi", city: "Manisa" },
  photos: [aPhoto(1), aPhoto(2)],
};

beforeEach(() => {
  fetchProduct.mockReset();
  notFound.mockClear();
});

const params = (slug: string) => ({ params: Promise.resolve({ slug }) });

describe("Ürün detayı sayfası", () => {
  it("ürünü fiyatı, stoku, üreticisi ve galerisiyle gösterir; küçük resim büyük görseli değiştirir", async () => {
    fetchProduct.mockResolvedValue(product);

    renderWithIntl(await ProductPage(params("dut")));

    expect(screen.getByRole("heading", { level: 1, name: "Dut" })).toBeDefined();
    expect(screen.getByText(/99,00/)).toBeDefined();
    expect(screen.getByText("Son 4 adet")).toBeDefined();
    expect(screen.getByText("Üretici Bahçesi")).toBeDefined();
    expect(screen.getByText("Ağaçtan yeni toplandı.")).toBeDefined();

    const main = screen.getByRole("img", { name: "Dut" });
    expect(main.getAttribute("src")).toBe(`/media/product-photos/${aPhoto(1).largeKey}`);
    fireEvent.click(screen.getByRole("button", { name: "2. fotoğrafı göster" }));
    expect(screen.getByRole("img", { name: "Dut" }).getAttribute("src")).toBe(`/media/product-photos/${aPhoto(2).largeKey}`);
    expect(screen.getByRole("button", { name: "2. fotoğrafı göster" }).getAttribute("aria-current")).toBe("true");
  });

  it("fotoğrafsız üründe galeri çizmez, açıklama yoksa bunu söyler", async () => {
    fetchProduct.mockResolvedValue({ ...product, photos: [], description: undefined });

    renderWithIntl(await ProductPage(params("dut")));

    expect(screen.queryByRole("img")).toBeNull();
    expect(screen.getByText("Üretici açıklama eklemedi.")).toBeDefined();
  });

  it("vitrinde görünmeyen ürün bulunamadı olur", async () => {
    fetchProduct.mockResolvedValue(null);

    await expect(ProductPage(params("taslak"))).rejects.toThrow("bulunamadı");
  });
});
