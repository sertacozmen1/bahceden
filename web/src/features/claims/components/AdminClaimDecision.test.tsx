import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { AdminClaimDetail } from "@/features/claims/api";
import { AdminClaimDecision } from "./AdminClaimDecision";

/**
 * Admin kararı (ADR-080, ADR-086): iade adedi satın alınana kadar artırılabilir,
 * para etkisi sunucudan önizlenir; ret gerekçeyle gönderilir. Ağ ve
 * yönlendirici taklit edilir (AGENTS.md §12.6).
 */

const router = { refresh: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

const money = (amount: number) => ({ amount, currency: "TRY" });
const detail: AdminClaimDetail = {
  claim: {
    id: 5,
    status: "ESCALATED",
    description: "Kasanın üst sırası ezik geldi.",
    lines: [{ orderItemId: 41, productName: "Domates", unit: "KILOGRAM", unitPrice: money(2450), claimedQuantity: 1 }],
    photos: [],
    sellerDeadlineAt: "2026-10-15T10:00:00Z",
    shippingRefunded: false,
    createdAt: "2026-10-13T10:00:00Z",
  },
  orderNumber: "BHC-7K2M9QXA4T",
  sellerOrderId: 17,
  sellerName: "Üretici Bahçesi",
  items: [
    {
      id: 41,
      productSlug: "domates",
      productName: "Domates",
      unit: "KILOGRAM",
      unitPrice: money(2450),
      quantity: 2,
      lineTotal: money(4900),
    },
  ],
  itemsTotal: money(4900),
  shippingFee: money(9900),
  decidable: true,
};

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function settlement(refund: number, payout: number) {
  return { refundAmount: money(refund), shippingRefunded: false, commissionAfter: money(523), payoutAfter: money(payout) };
}

type FetchCall = readonly [path: string, init?: RequestInit];

function stubFetch(previews: unknown[]) {
  const queue = [...previews];
  const fetchMock = vi.fn<(path: string, init?: RequestInit) => Promise<Response>>(async (path) =>
    path.endsWith("/preview") ? jsonResponse(200, queue.length > 1 ? queue.shift() : queue[0]) : jsonResponse(200, detail),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function decisionBody(calls: readonly FetchCall[]): unknown {
  const call = calls.find(([path]) => path === "/api/admin/claims/5/decision");
  if (call === undefined) {
    throw new Error("Karar isteği gönderilmedi");
  }
  return JSON.parse(String(call[1]?.body));
}

function lastButton(name: string): HTMLElement {
  const button = screen.getAllByRole("button", { name }).at(-1);
  if (button === undefined) {
    throw new Error("Onay düğmesi yok");
  }
  return button;
}

beforeEach(() => {
  router.refresh.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("AdminClaimDecision", () => {
  it("iade: alıcının adediyle başlar, satın alınana kadar artırılır ve REFUND gönderir", async () => {
    const fetchMock = stubFetch([settlement(2450, 6255), settlement(4900, 3927)]);
    renderWithIntl(<AdminClaimDecision detail={detail} />);

    fireEvent.click(screen.getByRole("button", { name: "İade et" }));
    await screen.findByText(/₺24,50 iade edilir/);
    fireEvent.click(screen.getByRole("button", { name: "Adedi artır" }));
    expect(await screen.findByText(/₺49,00 iade edilir/)).toBeDefined();
    expect(screen.getByRole("button", { name: "Adedi artır" }).hasAttribute("disabled")).toBe(true);

    fireEvent.click(lastButton("İade et"));
    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    expect(decisionBody(fetchMock.mock.calls)).toEqual({ decision: "REFUND", lines: [{ orderItemId: 41, quantity: 2 }] });
  });

  it("ret gerekçe ister ve gerekçeyle REJECT gönderir", async () => {
    const fetchMock = stubFetch([]);
    renderWithIntl(<AdminClaimDecision detail={detail} />);

    fireEvent.click(screen.getByRole("button", { name: "Reddet" }));
    fireEvent.change(await screen.findByLabelText("Gerekçe"), { target: { value: "Fotoğraflarda kusur görünmüyor." } });
    fireEvent.click(lastButton("Reddet"));

    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    expect(decisionBody(fetchMock.mock.calls)).toEqual({ decision: "REJECT", reason: "Fotoğraflarda kusur görünmüyor." });
  });
});
