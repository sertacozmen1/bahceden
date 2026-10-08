// @vitest-environment node
// Route Handler Node çalışma zamanında koşar; multipart gövdedeki dosya ancak Node'un kendi File/FormData sınıflarıyla doğru taşınır.
import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Satıcı başvurusu Route Handler'ı (ADR-023, add-seller-onboarding D3).
 * Siteler arası istek `api`'ye hiç ulaşmadan reddedilir; formun dışındaki
 * alanlar iletilmez. `api` çağrısı taklit edilir (K10).
 */

const submitSellerApplication = vi.fn();

vi.mock("@/features/seller/api", () => ({ submitSellerApplication }));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim" }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { POST, PUT } = await import("./route");

function aForm(): FormData {
  const form = new FormData();
  form.set("displayName", "Örnek Bahçe");
  form.set("city", "Manisa");
  form.set("phone", "05550000000");
  form.set("documentType", "CKS_CERTIFICATE");
  form.set("document", new File([new Uint8Array(8)], "belge.pdf", { type: "application/pdf" }));
  form.set("accountId", "999");
  return form;
}

function aRequest(method: "POST" | "PUT", site: string): Request {
  return new Request("http://localhost:3000/api/seller/application", {
    method,
    headers: { "sec-fetch-site": site },
    body: aForm(),
  });
}

beforeEach(() => {
  submitSellerApplication.mockReset();
});

describe("/api/seller/application", () => {
  it("başka siteden gelen isteği api'ye iletmeden 403 ile reddeder", async () => {
    const response = await POST(aRequest("POST", "cross-site"));

    expect(response.status).toBe(403);
    expect(submitSellerApplication).not.toHaveBeenCalled();
  });

  it("yalnızca formun alanlarını iletir ve api'nin durum kodunu korur", async () => {
    submitSellerApplication.mockResolvedValue({ status: 201, seller: { status: "PENDING" } });

    const response = await POST(aRequest("POST", "same-origin"));

    expect(response.status).toBe(201);
    const [method, form, token] = submitSellerApplication.mock.calls[0] as [string, FormData, string];
    expect(method).toBe("POST");
    expect(token).toBe("test-erisim");
    expect(form.get("displayName")).toBe("Örnek Bahçe");
    expect(form.get("document")).toHaveProperty("name", "belge.pdf");
    expect(form.get("accountId")).toBeNull();
  });

  it("yeniden gönderimi PUT olarak iletir", async () => {
    submitSellerApplication.mockResolvedValue({ status: 200, seller: { status: "PENDING" } });

    const response = await PUT(aRequest("PUT", "same-origin"));

    expect(response.status).toBe(200);
    expect(submitSellerApplication.mock.calls[0]?.[0]).toBe("PUT");
  });
});
