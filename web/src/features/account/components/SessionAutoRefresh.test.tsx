import { render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Sessiz oturum yenileme (ADR-070): süresi dolmuş oturumla açılan sayfa
 * yenileme ucuna gider ve aynı sayfaya döner; döngüye girmez; aynı anda
 * yenileyen başka bir sekme varsa onu bekler.
 */

const pathname = vi.hoisted(() => ({ value: "/" }));
vi.mock("next/navigation", () => ({ usePathname: () => pathname.value }));

const { SessionAutoRefresh } = await import("./SessionAutoRefresh");

const replace = vi.fn();

type LockCallback = (lock: { readonly name: string } | null) => unknown;

/** Başka bir sekmenin tuttuğu kilidi taklit eder. */
function stubLocksHeldByAnotherTab() {
  const request = vi.fn(async (name: string, ...rest: unknown[]) => {
    const [options, callback] = rest.length === 2 ? rest : [{}, rest[0]];
    const ifAvailable = typeof options === "object" && options !== null && "ifAvailable" in options;
    if (typeof callback !== "function") {
      throw new TypeError("geri çağrı yok");
    }
    const run: LockCallback = (lock) => callback(lock);
    return run(ifAvailable ? null : { name });
  });
  vi.stubGlobal("navigator", { ...navigator, locks: { request } });
}

beforeEach(() => {
  pathname.value = "/products";
  window.sessionStorage.clear();
  vi.stubGlobal("location", { ...window.location, pathname: "/products", search: "?q=elma", replace });
});

afterEach(() => {
  replace.mockReset();
  vi.unstubAllGlobals();
});

describe("Sessiz oturum yenileme", () => {
  it("yenileme ucuna sessiz kipte gider ve bulunduğu sayfayı dönüş yolu yapar", () => {
    render(<SessionAutoRefresh />);

    expect(replace).toHaveBeenCalledWith("/api/auth/refresh?next=%2Fproducts%3Fq%3Delma&silent=1");
  });

  it("bir dakika içinde ikinci kez denemez: yenileme döngüye girmez", () => {
    render(<SessionAutoRefresh />).unmount();
    render(<SessionAutoRefresh />);

    expect(replace).toHaveBeenCalledOnce();
  });

  it("korunan sayfanın ara sayfasında yenileme başlatmaz", () => {
    pathname.value = "/session/refresh";

    render(<SessionAutoRefresh />);

    expect(replace).not.toHaveBeenCalled();
  });

  it("başka sekme yeniliyorsa onu bekler ve yenileme yapmadan sayfaya döner", async () => {
    stubLocksHeldByAnotherTab();

    render(<SessionAutoRefresh />);

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith("/products?q=elma"));
    expect(replace).toHaveBeenCalledOnce();
  });
});
