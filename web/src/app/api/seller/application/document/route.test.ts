import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/apiError";

/**
 * Belge Route Handler'ı (add-seller-onboarding D6): `api`'nin koruma
 * başlıkları tarayıcıya aynen geçer; süresi geçmiş oturum yenilemeye
 * yönlendirilir. `api` çağrısı taklit edilir (K10).
 */

const downloadOwnSellerDocument = vi.fn();

vi.mock("@/features/seller/api", () => ({ downloadOwnSellerDocument }));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim" }));

const { GET } = await import("./route");

beforeEach(() => {
  downloadOwnSellerDocument.mockReset();
});

describe("GET /api/seller/application/document", () => {
  it("içeriği ve koruma başlıklarını iletir", async () => {
    downloadOwnSellerDocument.mockResolvedValue(
      new Response(new Uint8Array([37, 80, 68, 70, 45]), {
        status: 200,
        headers: {
          "Content-Type": "application/pdf",
          "Content-Disposition": 'inline; filename="belge.pdf"',
          "X-Content-Type-Options": "nosniff",
          "Content-Security-Policy": "sandbox",
          "Cache-Control": "no-store, private",
          "X-Internal": "iletilmemeli",
        },
      }),
    );

    const response = await GET();

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("application/pdf");
    expect(response.headers.get("x-content-type-options")).toBe("nosniff");
    expect(response.headers.get("content-security-policy")).toBe("sandbox");
    expect(response.headers.get("x-internal")).toBeNull();
    expect(new Uint8Array(await response.arrayBuffer())).toEqual(new Uint8Array([37, 80, 68, 70, 45]));
  });

  it("süresi geçmiş oturumu yenilemeye, aynı adrese dönecek şekilde yönlendirir", async () => {
    downloadOwnSellerDocument.mockRejectedValue(
      new ApiRequestError(401, { code: "AUTH_REQUIRED", message: "Giriş gerekli.", fieldErrors: [] }),
    );

    const response = await GET();

    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe(
      "/api/auth/refresh?next=%2Fapi%2Fseller%2Fapplication%2Fdocument",
    );
  });

  it("kaydı olmayan hesabın 404'ünü ortak hata gövdesiyle iletir", async () => {
    downloadOwnSellerDocument.mockRejectedValue(
      new ApiRequestError(404, { code: "SELLER_NOT_FOUND", message: "Satıcı kaydı bulunamadı.", fieldErrors: [] }),
    );

    const response = await GET();

    expect(response.status).toBe(404);
    expect(await response.json()).toMatchObject({ code: "SELLER_NOT_FOUND" });
  });
});
