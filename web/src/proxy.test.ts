import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { config, proxy } from "./proxy";

/**
 * Proxy (ADR-102): korunan yolda isteğin tam adresini başlığa yazar,
 * istemcinin gönderdiği aynı adlı başlığı ezer, çerezlere dokunmaz.
 * `NextResponse.next({ request: { headers } })` değiştirilen istek
 * başlıklarını yanıtta `x-middleware-request-*` olarak taşır.
 */

const FORWARDED = "x-middleware-request-x-bahceden-request-path";

describe("proxy", () => {
  it("korunan yolun yolunu ve sorgusunu başlığa yazar", () => {
    const response = proxy(new NextRequest("http://localhost:3000/admin/claims?status=CLOSED"));

    expect(response.headers.get(FORWARDED)).toBe("/admin/claims?status=CLOSED");
  });

  it("istemcinin gönderdiği başlığı ezer", () => {
    const request = new NextRequest("http://localhost:3000/seller/orders?status=NEW", {
      headers: { "x-bahceden-request-path": "/seller/payout" },
    });

    expect(proxy(request).headers.get(FORWARDED)).toBe("/seller/orders?status=NEW");
  });

  it("çerez yazmaz", () => {
    const response = proxy(new NextRequest("http://localhost:3000/account"));

    expect(response.headers.get("set-cookie")).toBeNull();
  });

  it("yalnızca oturum kapısı kullanan yollarda çalışır", () => {
    expect(config.matcher).toEqual([
      "/account/:path*",
      "/admin/:path*",
      "/seller/:path*",
      "/cart/:path*",
      "/checkout/:path*",
    ]);
  });
});
