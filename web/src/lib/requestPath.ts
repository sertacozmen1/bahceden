import type { Route } from "next";
import { safeReturnPath } from "@/lib/sameOrigin";

/**
 * Korunan sayfanın tam adresi (yol + sorgu), oturum kapısının dönüş adresi
 * için (ADR-102).
 *
 * Kapı çoğunlukla `layout.tsx` içinde çalışır ve Next.js layout'a sorgu
 * dizesini vermez. `proxy.ts` korunan yollarda isteğin yolunu ve sorgusunu
 * sunucu tarafında bu başlığa yazar; istemcinin gönderdiği aynı adlı başlık
 * ezilir.
 */
export const REQUEST_PATH_HEADER = "x-bahceden-request-path";

/** Next.js'in istemci gezintisi için eklediği iç parametre; dönüş adresine taşınmaz. */
const INTERNAL_PARAMS = ["_rsc"] as const;

/** Proxy'nin başlığa yazdığı değer: yol + iç parametreleri atılmış sorgu. */
export function requestPathOf(url: URL): string {
  const params = new URLSearchParams(url.search);
  for (const name of INTERNAL_PARAMS) {
    params.delete(name);
  }
  const query = params.toString();
  return query === "" ? url.pathname : `${url.pathname}?${query}`;
}

/**
 * Kapının dönüş adresi: başlıktaki adres iç yolsa ve kapının korunan yoluyla
 * başlıyorsa o; değilse korunan yolun kendisi. Böylece başlık yoksa ya da
 * beklenmeyen bir değer taşıyorsa davranış eskisi gibidir.
 */
export function returnPathFor(selfPath: Route, requested: string | null): Route {
  const candidate = safeReturnPath(requested, selfPath);
  const underSelf =
    candidate === selfPath || candidate.startsWith(`${selfPath}/`) || candidate.startsWith(`${selfPath}?`);
  return underSelf ? candidate : selfPath;
}
