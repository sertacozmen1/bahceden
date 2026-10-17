import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Talep açma Route Handler'ı (ADR-023, ADR-081). Siteler arası istek `api`'ye
 * ulaşmaz; yalnızca açıklama, satır alanları ve fotoğraflar iletilir. `api`
 * taklit edilir (K10).
 */

const openClaim = vi.fn();

vi.mock("@/features/claims/api", () => ({ openClaim }));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim" }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { POST } = await import("./route");

function aRequest(site: string, form: FormData): Request {
  return new Request("http://localhost:3000/api/orders/BHC-1/seller-orders/17/claim", {
    method: "POST",
    headers: { "sec-fetch-site": site },
    body: form,
  });
}

function aForm(): FormData {
  const form = new FormData();
  form.set("description", "Kasanın üst sırası ezik geldi.");
  form.set("lines[0].orderItemId", "41");
  form.set("lines[0].quantity", "2");
  form.set("sellerId", "999");
  form.set("lines[0].unitPrice", "1");
  form.append("photos", new File([new Uint8Array(4)], "ezik.jpg", { type: "image/jpeg" }));
  return form;
}

const context = (sellerOrderId: string) => ({ params: Promise.resolve({ number: "BHC-1", sellerOrderId }) });

beforeEach(() => {
  openClaim.mockReset();
});

describe("POST /api/orders/[number]/seller-orders/[sellerOrderId]/claim", () => {
  it("başka siteden gelen isteği api'ye iletmeden 403 ile reddeder", async () => {
    expect((await POST(aRequest("cross-site", aForm()), context("17"))).status).toBe(403);
    expect(openClaim).not.toHaveBeenCalled();
  });

  it("geçersiz kimliği api'ye iletmeden 404 döner", async () => {
    expect((await POST(aRequest("same-origin", aForm()), context("abc"))).status).toBe(404);
    expect(openClaim).not.toHaveBeenCalled();
  });

  it("yalnızca açıklamayı, satır alanlarını ve fotoğrafları iletir; 201 döner", async () => {
    openClaim.mockResolvedValue({ orderNumber: "BHC-1" });

    const response = await POST(aRequest("same-origin", aForm()), context("17"));

    expect(response.status).toBe(201);
    const [token, orderNumber, id, forwarded] = openClaim.mock.calls[0] as [string, string, number, FormData];
    expect(token).toBe("test-erisim");
    expect(orderNumber).toBe("BHC-1");
    expect(id).toBe(17);
    expect(forwarded.get("description")).toBe("Kasanın üst sırası ezik geldi.");
    expect(forwarded.get("lines[0].orderItemId")).toBe("41");
    expect(forwarded.get("lines[0].quantity")).toBe("2");
    expect(forwarded.get("sellerId")).toBeNull();
    expect(forwarded.get("lines[0].unitPrice")).toBeNull();
    expect(forwarded.getAll("photos")).toHaveLength(1);
  });
});
