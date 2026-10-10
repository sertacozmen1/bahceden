import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/apiError";

/**
 * Etiket Route Handler'ı (add-shipments D4): `api`'nin koruma başlıkları
 * tarayıcıya aynen geçer; süresi geçmiş oturum yenilemeye yönlendirilir;
 * geçersiz kimlik `api`'ye gitmez. `api` çağrısı taklit edilir (K10).
 */

const downloadShipmentLabel = vi.fn();

vi.mock("@/features/orders/api", () => ({ downloadShipmentLabel }));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim" }));

const { GET } = await import("./route");

const context = (id: string) => ({ params: Promise.resolve({ id }) });
const request = new Request("http://localhost:3000/api/seller/orders/17/label");

beforeEach(() => {
  downloadShipmentLabel.mockReset();
});

describe("GET /api/seller/orders/[id]/label", () => {
  it("etiketi ve koruma başlıklarını iletir", async () => {
    downloadShipmentLabel.mockResolvedValue(
      new Response(new Uint8Array([37, 80, 68, 70, 45]), {
        status: 200,
        headers: {
          "Content-Type": "application/pdf",
          "X-Content-Type-Options": "nosniff",
          "Content-Security-Policy": "sandbox",
          "Cache-Control": "no-store, private",
          "X-Internal": "iletilmemeli",
        },
      }),
    );

    const response = await GET(request, context("17"));

    expect(downloadShipmentLabel).toHaveBeenCalledWith("test-erisim", 17);
    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("application/pdf");
    expect(response.headers.get("content-security-policy")).toBe("sandbox");
    expect(response.headers.get("x-internal")).toBeNull();
  });

  it("süresi geçmiş oturumu yenilemeye, aynı adrese dönecek şekilde yönlendirir", async () => {
    downloadShipmentLabel.mockRejectedValue(
      new ApiRequestError(401, { code: "AUTH_REQUIRED", message: "Giriş gerekli.", fieldErrors: [] }),
    );

    const response = await GET(request, context("17"));

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("/api/auth/refresh?next=%2Fapi%2Fseller%2Forders%2F17%2Flabel");
  });

  it("geçersiz kimlik 404 olur ve api'ye gidilmez", async () => {
    const response = await GET(request, context("abc"));

    expect(response.status).toBe(404);
    expect(downloadShipmentLabel).not.toHaveBeenCalled();
  });
});
