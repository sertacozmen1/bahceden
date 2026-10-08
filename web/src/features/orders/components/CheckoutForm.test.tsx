import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { AddressView } from "@/features/addresses/api";
import { CheckoutForm } from "./CheckoutForm";

/**
 * Ödeme adımı (cart-and-checkout spec'i, D6, D10). Ağ taklit edilir;
 * yönlendirme test fonksiyonuna verilir (AGENTS.md §12.6).
 */

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn() }) }));

const provinces = [{ code: "35", name: "İzmir" }];

function anAddress(overrides: Partial<AddressView> = {}): AddressView {
  return {
    id: 5,
    title: "Ev",
    recipientName: "Örnek Alıcı",
    phone: "+905550000000",
    provinceCode: "35",
    provinceName: "İzmir",
    district: "Bornova",
    addressLine: "Ornek Mahallesi No: 1",
    deliverable: true,
    ...overrides,
  };
}

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("CheckoutForm", () => {
  it("seçili adresle sipariş verir ve dönen ödeme sayfasına gider", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(201, { orderNumber: "BHC-0000000001", paymentRedirectUrl: "/checkout/return?order=BHC-0000000001" }));
    vi.stubGlobal("fetch", fetchMock);
    const navigate = vi.fn();
    renderWithIntl(<CheckoutForm addresses={[anAddress()]} provinces={provinces} navigate={navigate} />);

    fireEvent.click(screen.getByRole("button", { name: "Ödemeye geç" }));

    await waitFor(() => expect(navigate).toHaveBeenCalledWith("/checkout/return?order=BHC-0000000001"));
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/orders");
    expect(JSON.parse(String(init.body))).toEqual({ addressId: 5 });
    const headers = new Headers(init.headers);
    expect(headers.get("Idempotency-Key")).toMatch(/^[0-9a-f-]{36}$/);
  });

  it("hatadan sonra yeniden denemede aynı tekrar anahtarını gönderir", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        jsonResponse(502, { code: "PAYMENT_PROVIDER_ERROR", message: "Ödeme kuruluşuna şu anda ulaşılamıyor.", fieldErrors: [] }),
      )
      .mockResolvedValueOnce(jsonResponse(201, { orderNumber: "BHC-0000000002", paymentRedirectUrl: "/odeme" }));
    vi.stubGlobal("fetch", fetchMock);
    const navigate = vi.fn();
    renderWithIntl(<CheckoutForm addresses={[anAddress()]} provinces={provinces} navigate={navigate} />);

    fireEvent.click(screen.getByRole("button", { name: "Ödemeye geç" }));
    expect(await screen.findByText("Ödeme kuruluşuna şu anda ulaşılamıyor.")).toBeDefined();
    fireEvent.click(screen.getByRole("button", { name: "Ödemeye geç" }));

    await waitFor(() => expect(navigate).toHaveBeenCalledWith("/odeme"));
    const keys = fetchMock.mock.calls.map(([, init]) => new Headers((init as RequestInit).headers).get("Idempotency-Key"));
    expect(keys[0]).toBe(keys[1]);
  });

  it("teslimat yapılabilen adres yoksa adres formunu açar ve ödeme düğmesi kapalıdır", () => {
    renderWithIntl(
      <CheckoutForm addresses={[anAddress({ deliverable: false })]} provinces={provinces} navigate={vi.fn()} />,
    );

    expect(screen.getByText("Yeni teslimat adresi")).toBeDefined();
    expect(screen.getByText(/onlar listelenmedi/)).toBeDefined();
    expect(screen.getByRole("button", { name: "Ödemeye geç" }).hasAttribute("disabled")).toBe(true);
  });
});
