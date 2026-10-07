import type { Route } from "next";

/**
 * Siteler arası istek savunması (design.md D6).
 *
 * Çerezler `SameSite=Lax`; buna ek olarak durum değiştiren her Route Handler
 * isteğin kendi sitemizden geldiğini tarayıcının eklediği başlıklardan
 * doğrular. Ayrı bir CSRF token tablosu veya gizli form alanı yoktur.
 *
 * Önce `Sec-Fetch-Site` okunur (tarayıcı koyar, betik değiştiremez); yoksa
 * `Origin` kendi kökenimizle karşılaştırılır; ikisi de yoksa istek reddedilir.
 */

/** `fetch` ile gelen durum değiştiren istek (giriş, kayıt, çıkış) kendi sitemizden mi? */
export function isSameOriginRequest(request: Request): boolean {
  const site = request.headers.get("sec-fetch-site");
  if (site !== null) {
    return site === "same-origin";
  }
  const origin = request.headers.get("origin");
  // Kendi kökenimiz istek URL'sinden değil Host başlığından okunur: konteynerde
  // Next kendi adresini makine adından kurar ve tarayıcının gördüğü adresle eşleşmez.
  const host = request.headers.get("host");
  if (origin === null || host === null) {
    return false;
  }
  try {
    return new URL(origin).host === host;
  } catch {
    return false;
  }
}

/**
 * Yenileme yönlendirmesi bir sayfa gezintisidir: kullanıcı başka bir siteden
 * (ör. e-postadaki bağlantı) korunan sayfaya gelmiş olabilir. Üst düzey
 * gezintiye her yerden izin verilir; siteler arası alt kaynak isteği (görsel,
 * `fetch`) reddedilir. Böylece yabancı bir sayfa arka planda eşzamanlı
 * yenilemeler tetikleyip oturum zincirini iptal ettiremez (design.md D1).
 */
export function isAllowedNavigation(request: Request): boolean {
  const site = request.headers.get("sec-fetch-site");
  if (site === null || site === "same-origin" || site === "none") {
    return true;
  }
  return request.headers.get("sec-fetch-mode") === "navigate";
}

/**
 * Giriş sonrası dönülecek adres yalnızca kendi sitemizde bir yol olabilir:
 * `//saldirgan.test` veya `https://…` açık yönlendirme olurdu.
 */
export function safeReturnPath(value: string | null | undefined, fallback: Route): Route {
  return value !== null && value !== undefined && isInternalPath(value) ? value : fallback;
}

/**
 * Çalışma zamanında doğrulanan iç yol. Tip daraltması bu denetime dayanır;
 * `as` ile zorlanmaz (AGENTS.md §12.3). Var olmayan bir yol sayfanın kendi
 * "bulunamadı" durumuna düşer, siteden çıkmaz.
 */
function isInternalPath(value: string): value is Route {
  return value.startsWith("/") && !value.startsWith("//") && !value.includes("\\");
}
