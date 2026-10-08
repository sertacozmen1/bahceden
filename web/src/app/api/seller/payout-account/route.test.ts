import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Ödeme bilgileri Route Handler'ı (ADR-023). Siteler arası istek `api`'ye
 * ulaşmaz; formun dışındaki alanlar iletilmez. `api` taklit edilir (K10).
 */

const savePayoutAccount = vi.fn();

vi.mock("@/features/payout/api", () => ({ savePayoutAccount }));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim" }));
vi.mock("next-intl/server", () => ({ getTranslations: async () => (key: string) => key }));

const { PUT } = await import("./route");

function aRequest(site: string, body: unknown): Request {
  return new Request("http://localhost:3000/api/seller/payout-account", {
    method: "PUT",
    headers: { "Content-Type": "application/json", "sec-fetch-site": site },
    body: JSON.stringify(body),
  });
}

const body = {
  legalType: "INDIVIDUAL",
  legalName: "Örnek Üretici",
  taxId: "10000000078",
  iban: "TR330006100519786457841326",
  sellerId: 999,
};

beforeEach(() => {
  savePayoutAccount.mockReset();
});

describe("PUT /api/seller/payout-account", () => {
  it("başka siteden gelen isteği api'ye iletmeden 403 ile reddeder", async () => {
    expect((await PUT(aRequest("cross-site", body))).status).toBe(403);
    expect(savePayoutAccount).not.toHaveBeenCalled();
  });

  it("yalnızca ödeme alanlarını iletir", async () => {
    savePayoutAccount.mockResolvedValue({ status: "ACTIVE" });

    const response = await PUT(aRequest("same-origin", body));

    expect(response.status).toBe(200);
    const [token, forwarded] = savePayoutAccount.mock.calls[0] as [string, Record<string, unknown>];
    expect(token).toBe("test-erisim");
    expect(forwarded).toMatchObject({ legalType: "INDIVIDUAL", taxId: "10000000078" });
    expect(forwarded).not.toHaveProperty("sellerId");
  });
});
