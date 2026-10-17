import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Talep fotoğrafı aktarımı (ADR-084). Geçersiz kimlik, konum veya boyut
 * `api`'ye gitmez; `api`'nin koruma başlıkları aynen geçer. `api` taklit edilir.
 */

const downloadClaimPhoto = vi.fn();

vi.mock("@/features/claims/api", () => ({ claimPhotoSizes: ["CARD", "LARGE"], downloadClaimPhoto }));
vi.mock("@/lib/session", () => ({ readAccessToken: async () => "test-erisim" }));

const { GET } = await import("./route");

const context = (claimId: string, position: string) => ({ params: Promise.resolve({ claimId, position }) });

beforeEach(() => {
  downloadClaimPhoto.mockReset();
});

describe("GET /media/claim-photos/[claimId]/[position]", () => {
  it("geçersiz kimlik, konum veya boyutu api'ye iletmeden 404 döner", async () => {
    const valid = "http://localhost:3000/media/claim-photos/5/0?size=CARD";
    expect((await GET(new Request(valid), context("abc", "0"))).status).toBe(404);
    expect((await GET(new Request(valid), context("5", "-1"))).status).toBe(404);
    expect((await GET(new Request("http://localhost:3000/media/claim-photos/5/0?size=HUGE"), context("5", "0"))).status)
      .toBe(404);
    expect(downloadClaimPhoto).not.toHaveBeenCalled();
  });

  it("fotoğrafı oturumun token'ıyla ister ve koruma başlıklarını iletir", async () => {
    downloadClaimPhoto.mockResolvedValue(
      new Response("jpeg", {
        headers: {
          "content-type": "image/jpeg",
          "cache-control": "no-store, private",
          "x-content-type-options": "nosniff",
          "set-cookie": "iletilmez=1",
        },
      }),
    );

    const response = await GET(new Request("http://localhost:3000/media/claim-photos/5/1?size=LARGE"), context("5", "1"));

    expect(downloadClaimPhoto).toHaveBeenCalledWith("test-erisim", 5, 1, "LARGE");
    expect(response.headers.get("content-type")).toBe("image/jpeg");
    expect(response.headers.get("cache-control")).toBe("no-store, private");
    expect(response.headers.get("x-content-type-options")).toBe("nosniff");
    expect(response.headers.get("set-cookie")).toBeNull();
  });
});
