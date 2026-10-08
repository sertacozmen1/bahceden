import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import { CancelSellerOrder } from "./CancelSellerOrder";

/**
 * Kargodan önce iptal (orders spec'i, ADR-056 §1, D9). Ağ ve yönlendirici
 * taklit edilir (AGENTS.md §12.6).
 */

const router = { refresh: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

beforeEach(() => {
  router.refresh.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("CancelSellerOrder", () => {
  it("alıcı gerekçesiz iptal edebilir; gerekçe boşsa null gider ve sayfa yenilenir", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, {}));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<CancelSellerOrder as="buyer" orderNumber="BHC-7K2M9QXA4T" sellerOrderId={17} sellerName="Üretici Bahçesi" />);

    fireEvent.click(screen.getByRole("button", { name: "Siparişi iptal et" }));
    expect(await screen.findByText("Üretici Bahçesi siparişini iptal et")).toBeDefined();
    expect(screen.getByLabelText("Gerekçe (isteğe bağlı)")).toBeDefined();
    fireEvent.click(screen.getByRole("button", { name: "İptal et" }));

    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/orders/BHC-7K2M9QXA4T/seller-orders/17/cancel");
    expect(JSON.parse(String(init.body))).toEqual({ reason: null });
  });

  it("satıcı gerekçesiz gönderemez; gerekçeyle gönderir", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, {}));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<CancelSellerOrder as="seller" sellerOrderId={17} />);

    fireEvent.click(screen.getByRole("button", { name: "Siparişi iptal et" }));
    fireEvent.click(await screen.findByRole("button", { name: "İptal et" }));
    expect(await screen.findByText("Gerekçe en az 10 karakter olmalıdır.")).toBeDefined();
    expect(fetchMock).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText("Gerekçe"), { target: { value: "Hasat beklenenden az oldu." } });
    fireEvent.click(screen.getByRole("button", { name: "İptal et" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/seller/orders/17/cancel");
    expect(JSON.parse(String(init.body))).toEqual({ reason: "Hasat beklenenden az oldu." });
  });

  it("sunucu hatasını (ör. iade yapılamadı) pencerede gösterir", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(502, { code: "PAYMENT_PROVIDER_ERROR", message: "Ödeme kuruluşuna şu anda ulaşılamıyor.", fieldErrors: [] }),
      ),
    );
    renderWithIntl(<CancelSellerOrder as="buyer" orderNumber="BHC-1" sellerOrderId={17} sellerName="Üretici" />);

    fireEvent.click(screen.getByRole("button", { name: "Siparişi iptal et" }));
    fireEvent.click(await screen.findByRole("button", { name: "İptal et" }));

    expect(await screen.findByText("Ödeme kuruluşuna şu anda ulaşılamıyor.")).toBeDefined();
    expect(router.refresh).not.toHaveBeenCalled();
  });
});
