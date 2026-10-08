import { fireEvent, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import { SellerApplicationForm } from "./SellerApplicationForm";

/**
 * Satıcı başvuru formu (seller-onboarding spec'i "Satıcı başvurusu", "Belge
 * dosyası doğrulanır…"). Ağ ve yönlendirici taklit edilir (AGENTS.md §12.6);
 * dosyalar sahte baytlardır (K10).
 */

const router = { refresh: vi.fn() };

vi.mock("next/navigation", () => ({ useRouter: () => router }));

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const pendingSeller = {
  slug: "ornek-bahce",
  displayName: "Örnek Bahçe",
  city: "Manisa",
  phone: "+905550000000",
  status: "PENDING",
  documentType: "CKS_CERTIFICATE",
  statusChangedAt: "2026-10-08T09:30:00Z",
};

function aPdf(size = 64): File {
  return new File([new Uint8Array(size)], "belge.pdf", { type: "application/pdf" });
}

function fillDeclaration() {
  fireEvent.change(screen.getByLabelText("Üretici adı"), { target: { value: "Örnek Bahçe" } });
  fireEvent.change(screen.getByLabelText("Şehir"), { target: { value: "Manisa" } });
  fireEvent.change(screen.getByLabelText("Telefon"), { target: { value: "0555 000 00 00" } });
}

function chooseDocument(file: File) {
  fireEvent.change(screen.getByLabelText("Belge"), { target: { files: [file] } });
}

function submit(name = "Başvuruyu gönder") {
  fireEvent.click(screen.getByRole("button", { name }));
}

beforeEach(() => {
  router.refresh.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("SellerApplicationForm", () => {
  it("belge türü ve dosya seçilmeden gönderilmez; iki alan da hatasını gösterir", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<SellerApplicationForm mode="apply" />);

    fillDeclaration();
    submit();

    expect(await screen.findByText("Belge türünü seçin.")).toBeDefined();
    expect(screen.getByText("Belge dosyasını seçin.")).toBeDefined();
    expect(screen.getByText("Formdaki işaretli alanları düzeltin.")).toBeDefined();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("10 MB'den büyük dosyayı sunucuya göndermeden reddeder", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<SellerApplicationForm mode="apply" />);

    fillDeclaration();
    fireEvent.click(screen.getByRole("radio", { name: /ÇKS belgesi/ }));
    chooseDocument(aPdf(10 * 1024 * 1024 + 1));
    submit();

    expect(await screen.findByText("Belge en çok 10 MB olabilir.")).toBeDefined();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("sunucunun belge hatasını dosya alanına bağlar", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(400, {
          code: "VALIDATION_FAILED",
          message: "Gönderilen değerler geçersiz.",
          fieldErrors: [{ field: "document", message: "Belge PDF, JPEG veya PNG olmalıdır" }],
        }),
      ),
    );
    renderWithIntl(<SellerApplicationForm mode="apply" />);

    fillDeclaration();
    fireEvent.click(screen.getByRole("radio", { name: /Vergi levhası/ }));
    chooseDocument(aPdf());
    submit();

    expect(await screen.findByText("Belge PDF, JPEG veya PNG olmalıdır")).toBeDefined();
    expect(screen.getByLabelText("Belge").getAttribute("aria-invalid")).toBe("true");
    expect(router.refresh).not.toHaveBeenCalled();
  });

  it("e-posta doğrulanmamış hatasını formun üstünde gösterir", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(422, {
          code: "SELLER_EMAIL_NOT_VERIFIED",
          message: "Satıcı başvurusu için önce e-posta adresinizi doğrulayın.",
          fieldErrors: [],
        }),
      ),
    );
    renderWithIntl(<SellerApplicationForm mode="apply" />);

    fillDeclaration();
    fireEvent.click(screen.getByRole("radio", { name: /ÇKS belgesi/ }));
    chooseDocument(aPdf());
    submit();

    expect(await screen.findByText("Satıcı başvurusu için önce e-posta adresinizi doğrulayın.")).toBeDefined();
  });

  it("başarılı başvuruda formu POST ile multipart olarak gönderir ve sayfayı yeniler", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(201, pendingSeller));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(<SellerApplicationForm mode="apply" />);

    fillDeclaration();
    fireEvent.click(screen.getByRole("radio", { name: /ÇKS belgesi/ }));
    chooseDocument(aPdf());
    submit();

    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(path).toBe("/api/seller/application");
    expect(init.method).toBe("POST");
    const body = init.body;
    expect(body).toBeInstanceOf(FormData);
    if (body instanceof FormData) {
      expect(body.get("displayName")).toBe("Örnek Bahçe");
      expect(body.get("documentType")).toBe("CKS_CERTIFICATE");
      expect(body.get("document")).toBeInstanceOf(File);
    }
    // Multipart sınırını tarayıcı yazar; elle Content-Type verilmez.
    expect(new Headers(init.headers).get("Content-Type")).toBeNull();
  });

  it("yeniden gönderimde önceki bilgilerle dolu gelir ve PUT kullanır", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, pendingSeller));
    vi.stubGlobal("fetch", fetchMock);
    renderWithIntl(
      <SellerApplicationForm
        mode="resubmit"
        previous={{ displayName: "Eski Ad", city: "İzmir", phone: "+905550000000", documentType: "TAX_CERTIFICATE" }}
      />,
    );

    expect(screen.getByLabelText<HTMLInputElement>("Üretici adı").value).toBe("Eski Ad");
    expect(screen.getByRole("radio", { name: /Vergi levhası/ }).getAttribute("aria-checked")).toBe("true");

    chooseDocument(aPdf());
    submit("Düzeltip yeniden gönder");

    await waitFor(() => expect(router.refresh).toHaveBeenCalled());
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(init.method).toBe("PUT");
  });
});
