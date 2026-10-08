import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Taslak ilan oluşturma Route Handler'ı (ADR-023). Siteler arası istek
 * `api`'ye ulaşmaz; formun dışındaki alanlar iletilmez. `api` taklit edilir (K10).
 */

const createListing = vi.fn();

vi.mock("@/features/listings/api", () => ({ createListing }));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim" }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { POST } = await import("./route");

function aRequest(site: string, body: unknown): Request {
  return new Request("http://localhost:3000/api/seller/products", {
    method: "POST",
    headers: { "Content-Type": "application/json", "sec-fetch-site": site },
    body: JSON.stringify(body),
  });
}

const body = { name: "Domates", unit: "KILOGRAM", priceAmount: 2450, stockQuantity: 5, sellerId: 999 };

beforeEach(() => {
  createListing.mockReset();
});

describe("POST /api/seller/products", () => {
  it("başka siteden gelen isteği api'ye iletmeden 403 ile reddeder", async () => {
    expect((await POST(aRequest("cross-site", body))).status).toBe(403);
    expect(createListing).not.toHaveBeenCalled();
  });

  it("yalnızca ilan alanlarını iletir ve api'nin durum kodunu korur", async () => {
    createListing.mockResolvedValue({ status: 201, listing: { id: 7 } });

    const response = await POST(aRequest("same-origin", body));

    expect(response.status).toBe(201);
    const [token, forwarded] = createListing.mock.calls[0] as [string, Record<string, unknown>];
    expect(token).toBe("test-erisim");
    expect(forwarded).toMatchObject({ name: "Domates", priceAmount: 2450 });
    expect(forwarded).not.toHaveProperty("sellerId");
  });
});
