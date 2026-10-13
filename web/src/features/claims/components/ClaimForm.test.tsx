import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { OrderItemView } from "@/features/orders/api";
import { ClaimForm } from "./ClaimForm";

/**
 * Ayıplı ürün talebi formu (defect-claims spec'i, ADR-081, ADR-086). Ağ ve
 * yönlendirici taklit edilir (AGENTS.md §12.6).
 */

const router = { push: vi.fn(), refresh: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

const money = (amount: number) => ({ amount, currency: "TRY" });
const items: OrderItemView[] = [
  { id: 41, productSlug: "domates", productName: "Domates", unit: "KILOGRAM", unitPrice: money(2450), quantity: 3, lineTotal: money(7350) },
  { id: 42, productSlug: "biber", productName: "Biber", unit: "KILOGRAM", unitPrice: money(1000), quantity: 2, lineTotal: money(2000) },
];

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function photo(name = "ezik.jpg", type = "image/jpeg", size = 1024): File {
  return new File([new Uint8Array(size)], name, { type });
}

function fill(description: string, photos: File[]) {
  fireEvent.change(screen.getByLabelText("Açıklama"), { target: { value: description } });
  fireEvent.change(screen.getByLabelText("Fotoğraflar"), { target: { files: photos } });
}

beforeEach(() => {
  router.push.mockReset();
  router.refresh.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("ClaimForm", () => {
  it("ürün, açıklama veya fotoğraf eksikse göndermez ve alanlarda söyler", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<ClaimForm orderNumber="BHC-7K2M9QXA4T" sellerOrderId={17} items={items} />);

    fill("kısa", []);
    fireEvent.click(screen.getByRole("button", { name: "Bildir" }));

    expect(await screen.findByText("En az bir ürün seçin.")).toBeDefined();
    expect(screen.getByText("Açıklama en az 10, en çok 1000 karakter olmalıdır.")).toBeDefined();
    expect(screen.getByText("En az 1, en çok 3 fotoğraf ekleyin.")).toBeDefined();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("dört fotoğrafı ve PDF'i erken reddeder", async () => {
    vi.stubGlobal("fetch", vi.fn());
    renderWithIntl(<ClaimForm orderNumber="BHC-1" sellerOrderId={17} items={items} />);
    fireEvent.click(screen.getByRole("checkbox", { name: /Domates/ }));

    fill("Kasanın üst sırası ezik geldi.", [photo(), photo(), photo(), photo()]);
    fireEvent.click(screen.getByRole("button", { name: "Bildir" }));
    expect(await screen.findByText("En az 1, en çok 3 fotoğraf ekleyin.")).toBeDefined();

    fill("Kasanın üst sırası ezik geldi.", [photo("belge.pdf", "application/pdf")]);
    fireEvent.click(screen.getByRole("button", { name: "Bildir" }));
    expect(await screen.findByText("Yalnızca JPEG veya PNG fotoğraf ekleyin.")).toBeDefined();
  });

  it("seçilen satırları adetleriyle, açıklamayı ve fotoğrafları gönderir; siparişe döner", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(201, {}));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<ClaimForm orderNumber="BHC-7K2M9QXA4T" sellerOrderId={17} items={items} />);

    fireEvent.click(screen.getByRole("checkbox", { name: /Domates/ }));
    fireEvent.click(screen.getByRole("button", { name: "Adedi artır" }));
    fill("Kasanın üst sırası ezik geldi.", [photo(), photo("ikinci.png", "image/png")]);
    fireEvent.click(screen.getByRole("button", { name: "Bildir" }));

    await waitFor(() => expect(router.push).toHaveBeenCalledWith("/account/orders/BHC-7K2M9QXA4T"));
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/orders/BHC-7K2M9QXA4T/seller-orders/17/claim");
    const body = init.body as FormData;
    expect(body.get("description")).toBe("Kasanın üst sırası ezik geldi.");
    expect(body.get("lines[0].orderItemId")).toBe("41");
    expect(body.get("lines[0].quantity")).toBe("2");
    expect(body.get("lines[1].orderItemId")).toBeNull();
    expect(body.getAll("photos")).toHaveLength(2);
  });

  it("sunucunun satır ve fotoğraf hatalarını ilgili yerde gösterir", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(400, {
          code: "VALIDATION_FAILED",
          message: "İstek geçersiz.",
          fieldErrors: [
            { field: "lines[0].quantity", message: "Adet satın aldığınızdan fazla olamaz" },
            { field: "photos", message: "Fotoğraf okunamadı" },
          ],
        }),
      ),
    );
    renderWithIntl(<ClaimForm orderNumber="BHC-1" sellerOrderId={17} items={items} />);

    fireEvent.click(screen.getByRole("checkbox", { name: /Biber/ }));
    fill("Biberlerin yarısı çürük geldi.", [photo()]);
    fireEvent.click(screen.getByRole("button", { name: "Bildir" }));

    expect(await screen.findByText("Adet satın aldığınızdan fazla olamaz")).toBeDefined();
    expect(screen.getByText("Fotoğraf okunamadı")).toBeDefined();
    expect(router.push).not.toHaveBeenCalled();
  });

  it("süre dolduysa sunucunun mesajını formun üstünde gösterir", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(422, { code: "CLAIM_WINDOW_CLOSED", message: "Sorun bildirme süresi doldu.", fieldErrors: [] }),
      ),
    );
    renderWithIntl(<ClaimForm orderNumber="BHC-1" sellerOrderId={17} items={items} />);

    fireEvent.click(screen.getByRole("checkbox", { name: /Domates/ }));
    fill("Kasanın üst sırası ezik geldi.", [photo()]);
    fireEvent.click(screen.getByRole("button", { name: "Bildir" }));

    expect(await screen.findByText("Sorun bildirme süresi doldu.")).toBeDefined();
  });
});
