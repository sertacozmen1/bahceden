import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { AddressView } from "@/features/addresses/api";
import { AddressForm } from "./AddressForm";

/**
 * Teslimat adresi formu (cart-and-checkout spec'i, D5). Ağ ve yönlendirici
 * taklit edilir; adres sahtedir (K10).
 */

const router = { refresh: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

const provinces = [
  { code: "34", name: "İstanbul" },
  { code: "35", name: "İzmir" },
  { code: "06", name: "Ankara" },
  { code: "45", name: "Manisa" },
];

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function fill() {
  fireEvent.change(screen.getByLabelText("Adres adı"), { target: { value: "Ev" } });
  fireEvent.change(screen.getByLabelText("Teslim alacak kişi"), { target: { value: "Örnek Alıcı" } });
  fireEvent.change(screen.getByLabelText("Telefon"), { target: { value: "0555 000 00 00" } });
  fireEvent.change(screen.getByLabelText("İlçe"), { target: { value: "Bornova" } });
  fireEvent.change(screen.getByLabelText("Açık adres"), { target: { value: "Ornek Mahallesi No: 1" } });
}

beforeEach(() => {
  router.refresh.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("AddressForm", () => {
  it("yalnızca teslimat illerini seçenek olarak gösterir ve seçilen ille POST eder", async () => {
    const saved = { id: 5 } as AddressView;
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(201, saved));
    vi.stubGlobal("fetch", fetchMock);
    const onSaved = vi.fn();
    renderWithIntl(<AddressForm provinces={provinces} onSaved={onSaved} />);

    expect(screen.getAllByRole("radio")).toHaveLength(4);
    fill();
    fireEvent.click(screen.getByRole("radio", { name: "İzmir" }));
    fireEvent.click(screen.getByRole("button", { name: "Adresi kaydet" }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(saved));
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/addresses");
    expect(JSON.parse(String(init.body))).toEqual({
      title: "Ev",
      recipientName: "Örnek Alıcı",
      phone: "0555 000 00 00",
      provinceCode: "35",
      district: "Bornova",
      addressLine: "Ornek Mahallesi No: 1",
      postalCode: null,
    });
    expect(router.refresh).toHaveBeenCalled();
  });

  it("sunucunun alan hatasını ilgili alana bağlar", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(400, {
          code: "VALIDATION_FAILED",
          message: "Gönderilen değerler geçersiz.",
          fieldErrors: [{ field: "phone", message: "Geçerli bir Türkiye telefon numarası girin (örn. 0555 000 00 00)" }],
        }),
      ),
    );
    renderWithIntl(<AddressForm provinces={provinces} />);

    fill();
    fireEvent.click(screen.getByRole("button", { name: "Adresi kaydet" }));

    expect(await screen.findByText("Geçerli bir Türkiye telefon numarası girin (örn. 0555 000 00 00)")).toBeDefined();
    expect(screen.getByLabelText("Telefon").getAttribute("aria-invalid")).toBe("true");
  });

  it("düzenlemede mevcut değerlerle gelir ve PUT eder", async () => {
    const address: AddressView = {
      id: 7,
      title: "İş",
      recipientName: "Örnek Alıcı",
      phone: "+905550000000",
      provinceCode: "06",
      provinceName: "Ankara",
      district: "Çankaya",
      addressLine: "Ornek Mahallesi No: 2",
      deliverable: true,
    };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, address));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<AddressForm provinces={provinces} address={address} />);

    expect(screen.getByLabelText<HTMLInputElement>("İlçe").value).toBe("Çankaya");
    expect(screen.getByRole("radio", { name: "Ankara" }).getAttribute("aria-checked")).toBe("true");
    fireEvent.click(screen.getByRole("button", { name: "Adresi kaydet" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/addresses/7");
    expect(init.method).toBe("PUT");
  });
});
