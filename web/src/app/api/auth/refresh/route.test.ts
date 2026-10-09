import { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/apiError";

/**
 * Yenileme ucu (ADR-023, ADR-070): normal yenileme başarısızlıkta giriş
 * ekranına, sessiz yenileme aynı sayfaya oturumsuz döner. `api` çağrısı ve
 * çerezler taklit edilir (K10).
 */

const refreshSession = vi.fn();
const readRefreshToken = vi.fn();
const writeSession = vi.fn();
const clearSession = vi.fn();

vi.mock("@/features/account/api", () => ({ refreshSession }));
vi.mock("@/lib/session", () => ({ readRefreshToken, writeSession, clearSession }));

const { GET } = await import("./route");

const rejected = new ApiRequestError(400, { code: "AUTH_TOKEN_INVALID", message: "Oturum sona erdi.", fieldErrors: [] });

function request(query: string): NextRequest {
  return new NextRequest(`http://localhost:3000/api/auth/refresh?${query}`, {
    headers: { "sec-fetch-site": "same-origin", "sec-fetch-mode": "navigate" },
  });
}

beforeEach(() => {
  refreshSession.mockReset();
  readRefreshToken.mockReset().mockResolvedValue("test-yenileme");
  writeSession.mockReset();
  clearSession.mockReset();
});

describe("GET /api/auth/refresh", () => {
  it("başarılı yenilemede çerezleri yazar ve geldiği sayfaya döner", async () => {
    refreshSession.mockResolvedValue({ accessToken: "a", refreshToken: "r" });

    const response = await GET(request("next=%2Fcart&silent=1"));

    expect(writeSession).toHaveBeenCalledOnce();
    expect(response.headers.get("location")).toBe("/cart");
  });

  it("korunan sayfadan gelen yenileme reddedilirse giriş ekranına gider", async () => {
    refreshSession.mockRejectedValue(rejected);

    const response = await GET(request("next=%2Forders"));

    expect(clearSession).toHaveBeenCalledOnce();
    expect(response.headers.get("location")).toBe("/login?next=%2Forders");
  });

  it("sessiz yenileme reddedilirse çerezleri siler ve aynı sayfaya oturumsuz döner", async () => {
    refreshSession.mockRejectedValue(rejected);

    const response = await GET(request("next=%2Fproducts%3Fq%3Delma&silent=1"));

    expect(clearSession).toHaveBeenCalledOnce();
    expect(response.headers.get("location")).toBe("/products?q=elma");
  });

  it("sessiz yenilemede yenileme çerezi yoksa aynı sayfaya döner", async () => {
    readRefreshToken.mockResolvedValue(undefined);

    const response = await GET(request("next=%2F&silent=1"));

    expect(refreshSession).not.toHaveBeenCalled();
    expect(response.headers.get("location")).toBe("/");
  });

  it("sessiz yenilemede api'ye ulaşılamazsa oturumu silmeden aynı sayfaya döner", async () => {
    refreshSession.mockRejectedValue(new TypeError("fetch failed"));

    const response = await GET(request("next=%2F&silent=1"));

    expect(clearSession).not.toHaveBeenCalled();
    expect(response.headers.get("location")).toBe("/");
  });

  it("sessiz yenilemede site dışı dönüş yolu kabul edilmez", async () => {
    refreshSession.mockRejectedValue(rejected);

    const response = await GET(request("next=%2F%2Fsaldirgan.test&silent=1"));

    expect(response.headers.get("location")).toBe("/account");
  });
});
