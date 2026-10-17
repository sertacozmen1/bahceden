import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { DefectClaimView } from "@/features/claims/api";
import { SellerClaimActions } from "./SellerClaimActions";

/**
 * Satıcının talebe yanıtı (ADR-080, ADR-086): her para hareketi sunucunun
 * önizlediği tutarla onaylanır; ekran tutar hesaplamaz. Ağ ve yönlendirici
 * taklit edilir (AGENTS.md §12.6).
 */

const router = { refresh: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

const money = (amount: number) => ({ amount, currency: "TRY" });
const claim: DefectClaimView = {
  id: 5,
  status: "OPEN",
  description: "Kasanın üst sırası ezik geldi.",
  lines: [{ orderItemId: 41, productName: "Domates", unit: "KILOGRAM", unitPrice: money(2450), claimedQuantity: 2 }],
  photos: [],
  sellerDeadlineAt: "2026-10-15T10:00:00Z",
  shippingRefunded: false,
  createdAt: "2026-10-13T10:00:00Z",
};

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function settlement(refund: number, payout: number, shippingRefunded = false) {
  return { refundAmount: money(refund), shippingRefunded, commissionAfter: money(523), payoutAfter: money(payout) };
}

/** Önizleme ve yanıt uçlarını adrese göre taklit eder; çağrılar sırayla kaydedilir. */
function stubFetch(previews: unknown[], response: Response = jsonResponse(200, {})) {
  const queue = [...previews];
  const fetchMock = vi.fn<(path: string, init?: RequestInit) => Promise<Response>>(async (path) =>
    path.endsWith("/preview") ? jsonResponse(200, queue.length > 1 ? queue.shift() : queue[0]) : response,
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

type FetchCall = readonly [path: string, init?: RequestInit];

function bodyOf(call: FetchCall | undefined): unknown {
  if (call === undefined) {
    throw new Error("Beklenen istek gönderilmedi");
  }
  return JSON.parse(String(call[1]?.body));
}

function responseCall(calls: readonly FetchCall[]): FetchCall | undefined {
  return calls.find(([path]) => path === "/api/seller/orders/17/claim/response");
}

beforeEach(() => {
  router.refresh.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("SellerClaimActions", () => {
  it("tamamını kabul: önizlenen tutarı gösterir, onayla ACCEPT gönderir ve sayfayı yeniler", async () => {
    const fetchMock = stubFetch([settlement(4900, 3927)]);
    renderWithIntl(<SellerClaimActions sellerOrderId={17} claim={claim} />);

    fireEvent.click(screen.getByRole("button", { name: "Tamamını kabul et" }));
    expect(await screen.findByText("Alıcıya ₺49,00 iade edilir; bu siparişteki hakedişiniz ₺39,27 olur.")).toBeDefined();
    expect(bodyOf(fetchMock.mock.calls[0])).toEqual({ lines: [] });

    fireEvent.click(screen.getByRole("button", { name: "Kabul et ve iade et" }));
    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    expect(bodyOf(responseCall(fetchMock.mock.calls))).toEqual({ decision: "ACCEPT" });
  });

  it("kısmi teklif: adet değişince yeniden önizler ve seçilen adetle OFFER gönderir", async () => {
    const fetchMock = stubFetch([settlement(4900, 3927), settlement(2450, 6255)]);
    renderWithIntl(<SellerClaimActions sellerOrderId={17} claim={claim} />);

    fireEvent.click(screen.getByRole("button", { name: "Kısmi iade öner" }));
    await screen.findByText(/₺49,00 iade edilir/);
    fireEvent.click(screen.getByRole("button", { name: "Adedi azalt" }));
    expect(await screen.findByText("Alıcıya ₺24,50 iade edilir; bu siparişteki hakedişiniz ₺62,55 olur.")).toBeDefined();

    fireEvent.click(screen.getByRole("button", { name: "Teklifi gönder" }));
    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    expect(bodyOf(responseCall(fetchMock.mock.calls))).toEqual({
      decision: "OFFER",
      lines: [{ orderItemId: 41, quantity: 1 }],
    });
  });

  it("tam iadede kargonun da döndüğünü söyler", async () => {
    stubFetch([settlement(19250, 0, true)]);
    renderWithIntl(<SellerClaimActions sellerOrderId={17} claim={claim} />);

    fireEvent.click(screen.getByRole("button", { name: "Tamamını kabul et" }));

    expect(await screen.findByText("Bütün ürünler iade edildiği için kargo ücreti de iade edilir.")).toBeDefined();
  });

  it("sunucu teklifi reddederse (toplam sıfır) mesajı gösterir ve onay kapalı kalır", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        jsonResponse(400, {
          code: "VALIDATION_FAILED",
          message: "İstek geçersiz.",
          fieldErrors: [{ field: "lines", message: "En az bir adet önerin; hiç iade etmeyecekseniz reddedin" }],
        }),
      ),
    );
    renderWithIntl(<SellerClaimActions sellerOrderId={17} claim={claim} />);

    fireEvent.click(screen.getByRole("button", { name: "Kısmi iade öner" }));

    expect(await screen.findByText("En az bir adet önerin; hiç iade etmeyecekseniz reddedin")).toBeDefined();
    expect((screen.getByRole("button", { name: "Teklifi gönder" }) as HTMLButtonElement).disabled).toBe(true);
  });

  it("ret gerekçe ister ve gerekçeyle REJECT gönderir", async () => {
    const fetchMock = stubFetch([]);
    renderWithIntl(<SellerClaimActions sellerOrderId={17} claim={claim} />);

    fireEvent.click(screen.getByRole("button", { name: "Reddet" }));
    fireEvent.change(await screen.findByLabelText("Gerekçe"), { target: { value: "Paket gönderilirken sağlamdı." } });
    const confirm = screen.getAllByRole("button", { name: "Reddet" }).at(-1);
    if (confirm === undefined) {
      throw new Error("Onay düğmesi yok");
    }
    fireEvent.click(confirm);

    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    expect(bodyOf(fetchMock.mock.calls[0])).toEqual({ decision: "REJECT", reason: "Paket gönderilirken sağlamdı." });
  });
});
