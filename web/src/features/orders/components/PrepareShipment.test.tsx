import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import { PrepareShipment } from "./PrepareShipment";

/**
 * Kargoya hazırla (shipments spec'i, add-shipments D3). Ağ ve yönlendirici
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

describe("PrepareShipment", () => {
  it("varsayılan orta boy; seçilen boyutla gönderi açar ve sayfayı yeniler", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(201, {}));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<PrepareShipment sellerOrderId={17} />);

    expect(screen.getByRole("radio", { name: /Orta/ }).getAttribute("aria-checked")).toBe("true");
    fireEvent.click(screen.getByRole("radio", { name: /Büyük/ }));
    fireEvent.click(screen.getByRole("button", { name: "Gönderi oluştur" }));

    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/seller/orders/17/shipment");
    expect(JSON.parse(String(init.body))).toEqual({ packageSize: "LARGE" });
  });

  it("sunucunun iş kuralı hatasını (telefon yok) olduğu gibi gösterir", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(422, {
          code: "SHIPMENT_SENDER_PHONE_MISSING",
          message: "Gönderi açmak için satıcı kaydınızda telefon numarası olmalı.",
          fieldErrors: [],
        }),
      ),
    );
    renderWithIntl(<PrepareShipment sellerOrderId={17} />);

    fireEvent.click(screen.getByRole("button", { name: "Gönderi oluştur" }));

    expect(await screen.findByText("Gönderi açmak için satıcı kaydınızda telefon numarası olmalı.")).toBeDefined();
    expect(router.refresh).not.toHaveBeenCalled();
  });

  it("sunucuya ulaşılamazsa genel hata gösterir ve düğme yeniden kullanılabilir olur", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("ağ yok")));
    renderWithIntl(<PrepareShipment sellerOrderId={17} />);

    fireEvent.click(screen.getByRole("button", { name: "Gönderi oluştur" }));

    expect(await screen.findByRole("alert")).toBeDefined();
    expect(screen.getByRole("button", { name: "Gönderi oluştur" }).hasAttribute("disabled")).toBe(false);
  });
});
