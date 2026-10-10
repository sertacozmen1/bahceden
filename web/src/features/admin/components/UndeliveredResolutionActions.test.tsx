import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import { UndeliveredResolutionActions } from "./UndeliveredResolutionActions";

/**
 * Teslim edilemeyen sipariş kararı (shipments spec'i, add-shipments D7).
 * Ağ ve yönlendirici taklit edilir (AGENTS.md §12.6).
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

describe("UndeliveredResolutionActions", () => {
  it("iade açıklamasız onaylanır; tutar pencerede görünür", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, {}));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<UndeliveredResolutionActions sellerOrderId={17} refundAmount="₺147,00" />);

    fireEvent.click(screen.getByRole("button", { name: "İade et" }));
    expect(await screen.findByText(/₺147,00/)).toBeDefined();
    fireEvent.click(screen.getAllByRole("button", { name: "İade et" }).at(-1) as HTMLElement);

    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/admin/seller-orders/17/resolution");
    expect(JSON.parse(String(init.body))).toEqual({ decision: "REFUND", reason: "" });
  });

  it("iadesiz kapatma gerekçe ister; gerekçeyle gönderir", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, {}));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<UndeliveredResolutionActions sellerOrderId={17} refundAmount="₺147,00" />);

    fireEvent.click(screen.getByRole("button", { name: "İadesiz kapat" }));
    fireEvent.click(await screen.findByRole("button", { name: "Kapat" }));
    expect(await screen.findByText("Gerekçe en az 10 karakter olmalıdır.")).toBeDefined();
    expect(fetchMock).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText("Gerekçe"), { target: { value: "Alıcı adreste yoktu ve şubeden almadı." } });
    fireEvent.click(screen.getByRole("button", { name: "Kapat" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(JSON.parse(String(init.body))).toEqual({ decision: "CLOSE", reason: "Alıcı adreste yoktu ve şubeden almadı." });
  });

  it("sunucu hatasını (iade yapılamadı) pencerede gösterir", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(502, { code: "PAYMENT_PROVIDER_ERROR", message: "Ödeme kuruluşuna şu anda ulaşılamıyor.", fieldErrors: [] }),
      ),
    );
    renderWithIntl(<UndeliveredResolutionActions sellerOrderId={17} refundAmount="₺147,00" />);

    fireEvent.click(screen.getByRole("button", { name: "İade et" }));
    await screen.findByText(/₺147,00/);
    fireEvent.click(screen.getAllByRole("button", { name: "İade et" }).at(-1) as HTMLElement);

    expect(await screen.findByText("Ödeme kuruluşuna şu anda ulaşılamıyor.")).toBeDefined();
    expect(router.refresh).not.toHaveBeenCalled();
  });
});
