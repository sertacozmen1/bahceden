import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { SellerListingView } from "@/features/listings/api";
import { ListingForm } from "./ListingForm";

/**
 * İlan bilgileri formu (product-listings spec'i "Onaylı satıcı ilan oluşturur
 * ve düzenler"). Ağ ve yönlendirici taklit edilir (AGENTS.md §12.6).
 */

const router = { push: vi.fn(), refresh: vi.fn() };

vi.mock("next/navigation", () => ({ useRouter: () => router }));

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const listing: SellerListingView = {
  id: 7,
  slug: "salkim-domates",
  name: "Salkım Domates",
  description: "Tarla domatesi.",
  unit: "KILOGRAM",
  unitPrice: { amount: 2450, currency: "TRY" },
  stockQuantity: 80,
  status: "DRAFT",
  updatedAt: "2026-10-08T09:30:00Z",
  photos: [],
};

function fill(name: string, price: string, stock: string) {
  fireEvent.change(screen.getByLabelText("Ürün adı"), { target: { value: name } });
  fireEvent.change(screen.getByLabelText("Birim fiyat (TL)"), { target: { value: price } });
  fireEvent.change(screen.getByLabelText("Stok miktarı"), { target: { value: stock } });
}

beforeEach(() => {
  router.push.mockReset();
  router.refresh.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ListingForm", () => {
  it("geçersiz fiyat ve stoku göndermeden işaretler", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<ListingForm mode="create" />);

    fill("Domates", "yirmi", "-3");
    fireEvent.click(screen.getByRole("button", { name: "Taslak olarak kaydet" }));

    expect(await screen.findByText("Geçerli bir fiyat girin (örn. 24,50).")).toBeDefined();
    expect(screen.getByText("Stok için 0 veya daha büyük bir tam sayı girin.")).toBeDefined();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("taslağı fiyatı kuruşa çevirerek oluşturur ve düzenleme sayfasına gider", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(201, listing));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<ListingForm mode="create" />);

    fill("Salkım Domates", "24,50", "80");
    fireEvent.click(screen.getByRole("radio", { name: "Demet" }));
    fireEvent.click(screen.getByRole("button", { name: "Taslak olarak kaydet" }));

    await waitFor(() => expect(router.push).toHaveBeenCalledWith("/seller/products/7"));
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/seller/products");
    expect(JSON.parse(String(init.body))).toEqual({
      name: "Salkım Domates",
      description: "",
      unit: "BUNCH",
      priceAmount: 2450,
      stockQuantity: 80,
    });
  });

  it("sunucunun alan hatasını ilgili alana bağlar", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(400, {
          code: "VALIDATION_FAILED",
          message: "Gönderilen değerler geçersiz.",
          fieldErrors: [{ field: "name", message: "İlan adı en az 2, en çok 200 karakter olmalıdır" }],
        }),
      ),
    );
    renderWithIntl(<ListingForm mode="create" />);

    fill("X", "10", "1");
    fireEvent.click(screen.getByRole("button", { name: "Taslak olarak kaydet" }));

    expect(await screen.findByText("İlan adı en az 2, en çok 200 karakter olmalıdır")).toBeDefined();
    expect(screen.getByLabelText("Ürün adı").getAttribute("aria-invalid")).toBe("true");
    expect(router.push).not.toHaveBeenCalled();
  });

  it("düzenlemede mevcut değerlerle gelir, PUT ile kaydeder ve sonucu bildirir", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, listing));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<ListingForm mode="edit" listing={listing} />);

    expect(screen.getByLabelText<HTMLInputElement>("Birim fiyat (TL)").value).toBe("24,50");
    expect(screen.getByLabelText<HTMLInputElement>("Stok miktarı").value).toBe("80");
    fireEvent.click(screen.getByRole("button", { name: "Değişiklikleri kaydet" }));

    expect(await screen.findByText("Değişiklikler kaydedildi.")).toBeDefined();
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/seller/products/7");
    expect(init.method).toBe("PUT");
    expect(router.refresh).toHaveBeenCalled();
  });
});
