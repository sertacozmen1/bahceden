import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import { BuyerClaimActions } from "./BuyerClaimActions";

/** Alıcının teklife veya redde yanıtı (ADR-080). Ağ ve yönlendirici taklit edilir. */

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

describe("BuyerClaimActions", () => {
  it("kabul ve yönetime taşıma doğru BFF yoluna gider ve sayfayı yeniler", async () => {
    const fetchMock = vi.fn<(path: string) => Promise<Response>>(async () => jsonResponse(200, {}));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<BuyerClaimActions orderNumber="BHC-7K2M9QXA4T" sellerOrderId={17} />);

    fireEvent.click(screen.getByRole("button", { name: "Kabul et" }));
    await waitFor(() => expect(router.refresh).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole("button", { name: "Bahçeden ekibine taşı" }));
    await waitFor(() => expect(router.refresh).toHaveBeenCalledTimes(2));

    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual([
      "/api/orders/BHC-7K2M9QXA4T/seller-orders/17/claim/accept",
      "/api/orders/BHC-7K2M9QXA4T/seller-orders/17/claim/escalate",
    ]);
  });

  it("sunucu hatasını gösterir", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(409, {
          code: "CLAIM_INVALID_TRANSITION",
          message: "Bu talep için şu anda bu işlem yapılamaz.",
          fieldErrors: [],
        }),
      ),
    );
    renderWithIntl(<BuyerClaimActions orderNumber="BHC-1" sellerOrderId={17} />);

    fireEvent.click(screen.getByRole("button", { name: "Kabul et" }));

    expect(await screen.findByText("Bu talep için şu anda bu işlem yapılamaz.")).toBeDefined();
    expect(router.refresh).not.toHaveBeenCalled();
  });
});
