import { describe, expect, it } from "vitest";
import { isAllowedNavigation, isSameOriginRequest, safeReturnPath } from "./sameOrigin";

/**
 * Siteler arası istek ve açık yönlendirme savunması (design.md D6).
 * Güvenlik kuralı olduğu için her dal sabitlenir.
 */

function aRequest(headers: Record<string, string>): Request {
  return new Request("http://localhost:3000/api/auth/login", { method: "POST", headers });
}

describe("isSameOriginRequest", () => {
  it("aynı kökenden gelen isteği kabul eder", () => {
    expect(isSameOriginRequest(aRequest({ "sec-fetch-site": "same-origin" }))).toBe(true);
  });

  it("başka siteden gelen isteği reddeder", () => {
    expect(isSameOriginRequest(aRequest({ "sec-fetch-site": "cross-site" }))).toBe(false);
    expect(isSameOriginRequest(aRequest({ "sec-fetch-site": "same-site" }))).toBe(false);
  });

  it("Sec-Fetch-Site yoksa Origin karşılaştırılır", () => {
    expect(isSameOriginRequest(aRequest({ origin: "http://localhost:3000", host: "localhost:3000" }))).toBe(true);
    expect(isSameOriginRequest(aRequest({ origin: "https://saldirgan.test", host: "localhost:3000" }))).toBe(false);
  });

  it("Origin, isteğin URL'sine değil Host başlığına göre karşılaştırılır (konteyner adı)", () => {
    const request = new Request("http://529c39dd06c7:3000/api/auth/login", {
      method: "POST",
      headers: { origin: "http://localhost:3000", host: "localhost:3000" },
    });
    expect(isSameOriginRequest(request)).toBe(true);
  });

  it("iki başlık da yoksa reddeder", () => {
    expect(isSameOriginRequest(aRequest({}))).toBe(false);
  });
});

describe("isAllowedNavigation", () => {
  it("başka siteden gelen üst düzey gezintiye izin verir", () => {
    expect(isAllowedNavigation(aRequest({ "sec-fetch-site": "cross-site", "sec-fetch-mode": "navigate" }))).toBe(true);
  });

  it("başka siteden gelen alt kaynak isteğini reddeder", () => {
    expect(isAllowedNavigation(aRequest({ "sec-fetch-site": "cross-site", "sec-fetch-mode": "no-cors" }))).toBe(false);
  });
});

describe("safeReturnPath", () => {
  it("kendi sitemizdeki yolu kabul eder", () => {
    expect(safeReturnPath("/account", "/")).toBe("/account");
  });

  it("açık yönlendirmeye yol açacak değerleri reddeder", () => {
    for (const value of ["//saldirgan.test", "https://saldirgan.test", "/\\saldirgan.test", "hesabim", ""]) {
      expect(safeReturnPath(value, "/")).toBe("/");
    }
  });

  it("değer yoksa varsayılana döner", () => {
    expect(safeReturnPath(null, "/account")).toBe("/account");
    expect(safeReturnPath(undefined, "/account")).toBe("/account");
  });
});
