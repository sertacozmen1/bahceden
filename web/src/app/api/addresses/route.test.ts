import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Adres ekleme Route Handler'ı (ADR-023). Siteler arası istek `api`'ye
 * ulaşmaz; adres alanları dışındakiler iletilmez. `api` taklit edilir (K10).
 */

const createAddress = vi.fn();

vi.mock("@/features/addresses/api", () => ({ createAddress }));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim" }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { POST } = await import("./route");

function aRequest(site: string, body: unknown): Request {
  return new Request("http://localhost:3000/api/addresses", {
    method: "POST",
    headers: { "Content-Type": "application/json", "sec-fetch-site": site },
    body: JSON.stringify(body),
  });
}

const body = { title: "Ev", provinceCode: "35", accountId: 999 };

beforeEach(() => {
  createAddress.mockReset();
});

describe("POST /api/addresses", () => {
  it("başka siteden gelen isteği api'ye iletmeden 403 ile reddeder", async () => {
    expect((await POST(aRequest("cross-site", body))).status).toBe(403);
    expect(createAddress).not.toHaveBeenCalled();
  });

  it("yalnızca adres alanlarını iletir ve api'nin durum kodunu korur", async () => {
    createAddress.mockResolvedValue({ status: 201, address: { id: 5 } });

    const response = await POST(aRequest("same-origin", body));

    expect(response.status).toBe(201);
    const [, forwarded] = createAddress.mock.calls[0] as [string, Record<string, unknown>];
    expect(forwarded).toMatchObject({ title: "Ev", provinceCode: "35" });
    expect(forwarded).not.toHaveProperty("accountId");
  });
});
