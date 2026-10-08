import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { SellerListingView } from "@/features/listings/api";
import { PhotoManager } from "./PhotoManager";

/**
 * Fotoğraf yöneticisi (product-listings spec'i "İlan fotoğrafları"). Ağ ve
 * yönlendirici taklit edilir; dosyalar sahte baytlardır (K10).
 */

const router = { refresh: vi.fn() };

vi.mock("next/navigation", () => ({ useRouter: () => router }));

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function aPhoto(id: number) {
  return { id, cardKey: String(id).padStart(32, "a"), largeKey: String(id).padStart(32, "b"), width: 1600, height: 1200 };
}

function aListing(photoCount: number): SellerListingView {
  return {
    id: 7,
    slug: "domates",
    name: "Domates",
    unit: "KILOGRAM",
    unitPrice: { amount: 2450, currency: "TRY" },
    stockQuantity: 10,
    dispatchDays: 2,
    status: "DRAFT",
    updatedAt: "2026-10-08T09:30:00Z",
    photos: Array.from({ length: photoCount }, (_, index) => aPhoto(index + 11)),
  };
}

beforeEach(() => {
  router.refresh.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("PhotoManager", () => {
  it("fotoğrafı tek dosya olarak yükler ve sayfayı yeniler", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, aListing(1)));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<PhotoManager listing={aListing(0)} />);

    expect(screen.getByText(/Henüz fotoğraf yok/)).toBeDefined();
    const file = new File([new Uint8Array(64)], "domates.jpg", { type: "image/jpeg" });
    fireEvent.change(screen.getByLabelText("Fotoğraf dosyası"), { target: { files: [file] } });

    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/seller/products/7/photos");
    expect(init.body).toBeInstanceOf(FormData);
  });

  it("büyük dosyayı ve yanlış türü göndermeden reddeder", () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<PhotoManager listing={aListing(0)} />);

    const big = new File([new Uint8Array(10 * 1024 * 1024 + 1)], "buyuk.jpg", { type: "image/jpeg" });
    fireEvent.change(screen.getByLabelText("Fotoğraf dosyası"), { target: { files: [big] } });
    expect(screen.getByText("Fotoğraf en çok 10 MB olabilir.")).toBeDefined();

    const pdf = new File([new Uint8Array(8)], "belge.pdf", { type: "application/pdf" });
    fireEvent.change(screen.getByLabelText("Fotoğraf dosyası"), { target: { files: [pdf] } });
    expect(screen.getByText("Fotoğraf JPEG veya PNG olmalıdır.")).toBeDefined();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("sunucunun fotoğraf hatasını gösterir", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(400, {
          code: "VALIDATION_FAILED",
          message: "Gönderilen değerler geçersiz.",
          fieldErrors: [{ field: "photo", message: "Fotoğraf okunamadı; başka bir fotoğraf deneyin" }],
        }),
      ),
    );
    renderWithIntl(<PhotoManager listing={aListing(0)} />);

    fireEvent.change(screen.getByLabelText("Fotoğraf dosyası"), {
      target: { files: [new File([new Uint8Array(8)], "bozuk.jpg", { type: "image/jpeg" })] },
    });

    expect(await screen.findByText("Fotoğraf okunamadı; başka bir fotoğraf deneyin")).toBeDefined();
    expect(router.refresh).not.toHaveBeenCalled();
  });

  it("ilk fotoğraf kapaktır; öne alma yeni sırayı PUT ile gönderir", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, aListing(3)));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<PhotoManager listing={aListing(3)} />);

    expect(screen.getByText("Kapak")).toBeDefined();
    expect(screen.getByRole("button", { name: "1. fotoğrafı öne al" }).hasAttribute("disabled")).toBe(true);
    fireEvent.click(screen.getByRole("button", { name: "3. fotoğrafı öne al" }));

    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/seller/products/7/photos/order");
    expect(init.method).toBe("PUT");
    expect(JSON.parse(String(init.body))).toEqual({ photoIds: [11, 13, 12] });
  });

  it("silme onay ister ve DELETE gönderir", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, aListing(1)));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<PhotoManager listing={aListing(2)} />);

    fireEvent.click(screen.getByRole("button", { name: "2. fotoğrafı sil" }));
    fireEvent.click(await screen.findByRole("button", { name: "Sil" }));

    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/seller/products/7/photos/12");
    expect(init.method).toBe("DELETE");
  });

  it("5 fotoğrafta ekleme yerine sınır bilgisini gösterir", () => {
    renderWithIntl(<PhotoManager listing={aListing(5)} />);

    expect(screen.getByText("5 fotoğraf sınırına ulaştınız.")).toBeDefined();
    expect(screen.queryByRole("button", { name: "Fotoğraf ekle" })).toBeNull();
  });
});
