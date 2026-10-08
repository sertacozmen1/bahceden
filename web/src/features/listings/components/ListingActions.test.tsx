import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { SellerListingView } from "@/features/listings/api";
import { ListingActions } from "./ListingActions";

/**
 * Satıcının yayın eylemleri (product-listings spec'i "İlan yayın durumları").
 * Ağ ve yönlendirici taklit edilir.
 */

const router = { refresh: vi.fn() };

vi.mock("next/navigation", () => ({ useRouter: () => router }));

function aListing(status: SellerListingView["status"], photoCount = 1): SellerListingView {
  return {
    id: 7,
    slug: "domates",
    name: "Domates",
    unit: "KILOGRAM",
    unitPrice: { amount: 2450, currency: "TRY" },
    stockQuantity: 10,
    status,
    updatedAt: "2026-10-08T09:30:00Z",
    photos: Array.from({ length: photoCount }, (_, index) => ({
      id: index + 1,
      cardKey: "a".repeat(32),
      largeKey: "b".repeat(32),
      width: 10,
      height: 10,
    })),
  };
}

beforeEach(() => {
  router.refresh.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ListingActions", () => {
  it("fotoğrafsız taslakta yayına alma pasif ve nedeni yazılı", () => {
    renderWithIntl(<ListingActions listing={aListing("DRAFT", 0)} />);

    expect(screen.getByRole("button", { name: "Yayına al" }).hasAttribute("disabled")).toBe(true);
    expect(screen.getByText("Yayına almak için önce bir fotoğraf ekleyin.")).toBeDefined();
  });

  it("fotoğraflı taslağı yayına alır", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(aListing("PUBLISHED")), { status: 200, headers: { "Content-Type": "application/json" } }),
    );
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<ListingActions listing={aListing("DRAFT")} />);

    fireEvent.click(screen.getByRole("button", { name: "Yayına al" }));

    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/seller/products/7/publish");
  });

  it("geçiş tablosuna göre eylemleri gösterir; kaldırılmış ilanda eylem yok", () => {
    const published = renderWithIntl(<ListingActions listing={aListing("PUBLISHED")} />);
    expect(screen.getByRole("button", { name: "Yayından çek" })).toBeDefined();
    expect(screen.getByRole("link", { name: "Vitrinde gör" }).getAttribute("href")).toBe("/products/domates");
    published.unmount();

    const archived = renderWithIntl(<ListingActions listing={aListing("ARCHIVED")} />);
    expect(screen.getByRole("button", { name: "Arşivden geri al" })).toBeDefined();
    expect(screen.queryByRole("button", { name: "Yayına al" })).toBeNull();
    archived.unmount();

    renderWithIntl(<ListingActions listing={aListing("REMOVED")} />);
    expect(screen.queryAllByRole("button")).toHaveLength(0);
  });
});
