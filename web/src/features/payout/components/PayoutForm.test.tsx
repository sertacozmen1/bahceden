import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { PayoutAccountView } from "@/features/payout/api";
import { PayoutForm } from "./PayoutForm";

/**
 * Satıcının ödeme bilgileri formu (seller-payout-details spec'i, D2). Ağ ve
 * yönlendirici taklit edilir (AGENTS.md §12.6). Değerler sahte ama sağlamaları
 * geçerli örneklerdir (K10).
 */

const router = { push: vi.fn(), refresh: vi.fn() };

vi.mock("next/navigation", () => ({ useRouter: () => router }));

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const saved: PayoutAccountView = {
  legalType: "INDIVIDUAL",
  legalName: "Örnek Üretici",
  taxIdMasked: "*******0078",
  ibanMasked: "TR** **** **** **** **** **13 26",
  status: "ACTIVE",
  updatedAt: "2026-10-08T09:30:00Z",
};

function fillNumbers(taxId: string, iban: string) {
  fireEvent.change(screen.getByLabelText("T.C. kimlik numarası"), { target: { value: taxId } });
  fireEvent.change(screen.getByLabelText("IBAN"), { target: { value: iban } });
}

beforeEach(() => {
  router.refresh.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("PayoutForm", () => {
  it("bireysel türde vergi dairesi sormaz; şirket seçilince vergi dairesi ve VKN etiketi gelir", () => {
    renderWithIntl(<PayoutForm account={null} />);

    expect(screen.queryByLabelText("Vergi dairesi")).toBeNull();
    expect(screen.getByLabelText("Ad soyad")).toBeDefined();

    fireEvent.click(screen.getByRole("radio", { name: "Şirket" }));

    expect(screen.getByLabelText("Vergi dairesi")).toBeDefined();
    expect(screen.getByLabelText("Şirket unvanı")).toBeDefined();
    expect(screen.getByLabelText("Vergi kimlik numarası")).toBeDefined();
  });

  it("bilgileri PUT ile gönderir, başarıyı bildirir ve numara alanlarını boşaltır", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, saved));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<PayoutForm account={null} />);

    fireEvent.change(screen.getByLabelText("Ad soyad"), { target: { value: "Örnek Üretici" } });
    fillNumbers("10000000078", "TR33 0006 1005 1978 6457 8413 26");
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));

    expect(await screen.findByText("Ödeme bilgileriniz kaydedildi.")).toBeDefined();
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/seller/payout-account");
    expect(init.method).toBe("PUT");
    expect(JSON.parse(String(init.body))).toEqual({
      legalType: "INDIVIDUAL",
      legalName: "Örnek Üretici",
      taxId: "10000000078",
      taxOffice: null,
      iban: "TR33 0006 1005 1978 6457 8413 26",
    });
    expect(screen.getByLabelText<HTMLInputElement>("IBAN").value).toBe("");
    expect(screen.getByLabelText<HTMLInputElement>("T.C. kimlik numarası").value).toBe("");
    expect(router.refresh).toHaveBeenCalled();
  });

  it("sunucunun alan hatasını ilgili alana bağlar", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(400, {
          code: "VALIDATION_FAILED",
          message: "Gönderilen değerler geçersiz.",
          fieldErrors: [{ field: "iban", message: "Geçerli bir TR IBAN girin (TR ve 24 hane)" }],
        }),
      ),
    );
    renderWithIntl(<PayoutForm account={saved} />);

    expect(screen.getByLabelText<HTMLInputElement>("Ad soyad").value).toBe("Örnek Üretici");
    fillNumbers("10000000078", "TR00");
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));

    expect(await screen.findByText("Geçerli bir TR IBAN girin (TR ve 24 hane)")).toBeDefined();
    expect(screen.getByLabelText("IBAN").getAttribute("aria-invalid")).toBe("true");
  });

  it("sağlayıcı reddederse mesajı gösterir ve yeni durumu görmek için sayfayı yeniler", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(422, {
          code: "PAYOUT_ACCOUNT_REJECTED",
          message: "Ödeme kuruluşu bilgilerinizi kabul etmedi.",
          fieldErrors: [],
        }),
      ),
    );
    renderWithIntl(<PayoutForm account={null} />);

    fireEvent.change(screen.getByLabelText("Ad soyad"), { target: { value: "Örnek Üretici" } });
    fillNumbers("10000000078", "TR330006100519786457841326");
    fireEvent.click(screen.getByRole("button", { name: "Kaydet" }));

    expect(await screen.findByText("Ödeme kuruluşu bilgilerinizi kabul etmedi.")).toBeDefined();
    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
  });
});
