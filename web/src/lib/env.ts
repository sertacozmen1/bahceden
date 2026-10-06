/**
 * Doğrulanmış ortam okuma.
 *
 * Eksik bir değişkenle sessizce çalışmak yerine açıkça hata verilir: yanlış
 * yapılandırma isteğin ortasında garip bir ağ hatası olarak değil, ilk kullanımda
 * anlaşılır bir mesajla görünür (AGENTS.md §12.3).
 *
 * Bu modül yalnızca SUNUCU tarafında çağrılır. Tarayıcıya gidecek değerler
 * `NEXT_PUBLIC_` öneki ile açıkça işaretlenir; API adresi gizli olmasa da
 * istemciye verilmez: istemci her zaman kendi Route Handler'larına konuşur.
 */

function requireEnv(name: string): string {
  const value = process.env[name];
  if (value === undefined || value.trim() === "") {
    throw new Error(
      `Ortam değişkeni tanımsız: ${name}. Örnek değerler için web/.env.example dosyasına bakın.`,
    );
  }
  return value;
}

/** `api` uygulamasının temel adresi (sunucu tarafı). */
export function apiBaseUrl(): string {
  return requireEnv("BAHCEDEN_API_BASE_URL").replace(/\/$/, "");
}
