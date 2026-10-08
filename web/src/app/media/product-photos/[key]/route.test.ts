// @vitest-environment node
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/apiError";

/**
 * Fotoğraf aktarma ucu (add-product-listings D5): içerik ve önbellek
 * başlıkları aynen geçer; olmayan fotoğraf 404. `api` taklit edilir (K10).
 */

const apiDownload = vi.fn();
vi.mock("@/lib/apiClient", () => ({ apiDownload }));

const { GET } = await import("./route");

const key = "a".repeat(32);
const context = { params: Promise.resolve({ key }) };

beforeEach(() => {
  apiDownload.mockReset();
});

describe("GET /media/product-photos/[key]", () => {
  it("fotoğrafı uzun önbellek başlığıyla iletir, oturum kullanmaz", async () => {
    apiDownload.mockResolvedValue(
      new Response(new Uint8Array([0xff, 0xd8, 0xff]), {
        headers: {
          "Content-Type": "image/jpeg",
          "Cache-Control": "max-age=31536000, public, immutable",
          "X-Content-Type-Options": "nosniff",
        },
      }),
    );

    const response = await GET(new Request(`http://localhost:3000/media/product-photos/${key}`), context);

    expect(response.status).toBe(200);
    expect(response.headers.get("cache-control")).toContain("immutable");
    expect(response.headers.get("content-type")).toBe("image/jpeg");
    expect(apiDownload).toHaveBeenCalledWith(`/api/v1/product-photos/${key}`, undefined);
  });

  it("olmayan fotoğraf 404", async () => {
    apiDownload.mockRejectedValue(
      new ApiRequestError(404, { code: "PRODUCT_NOT_FOUND", message: "İlan bulunamadı.", fieldErrors: [] }),
    );

    const response = await GET(new Request(`http://localhost:3000/media/product-photos/${key}`), context);

    expect(response.status).toBe(404);
  });
});
