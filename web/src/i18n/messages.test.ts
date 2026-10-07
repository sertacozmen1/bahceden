import { createTranslator } from "next-intl";
import { describe, expect, it } from "vitest";
import { messagesFor } from "./messages";

/**
 * Mesaj dosyalarının bütünlüğü (ADR-029).
 *
 * Tip güvenliği yalnızca kaynak dil (`tr.json`) üzerinden çalışır; İngilizce
 * dosyadaki eksik anahtar derlemede değil burada yakalanır.
 */

function keysOf(value: unknown, prefix = ""): string[] {
  if (typeof value !== "object" || value === null) {
    return [prefix];
  }
  return Object.entries(value).flatMap(([key, child]) => keysOf(child, prefix === "" ? key : `${prefix}.${key}`));
}

function leavesOf(value: unknown): unknown[] {
  if (typeof value !== "object" || value === null) {
    return [value];
  }
  return Object.values(value).flatMap(leavesOf);
}

describe("mesaj dosyaları", () => {
  it("İngilizce dosya Türkçe ile birebir aynı anahtarları taşır", () => {
    expect(keysOf(messagesFor("en")).sort()).toEqual(keysOf(messagesFor("tr")).sort());
  });

  it("hiçbir metin boş değildir", () => {
    for (const locale of ["tr", "en"] as const) {
      for (const leaf of leavesOf(messagesFor(locale))) {
        expect(typeof leaf === "string" && leaf.trim() !== "").toBe(true);
      }
    }
  });

  it("çoğul ve seçim biçimleri her dilde doğru çözülür", () => {
    const tr = createTranslator({ locale: "tr", messages: messagesFor("tr") });
    const en = createTranslator({ locale: "en", messages: messagesFor("en") });

    expect(tr("storefront.resultCount", { count: 1 })).toBe("1 ürün");
    expect(en("storefront.resultCount", { count: 1 })).toBe("1 product");
    expect(en("storefront.resultCount", { count: 5 })).toBe("5 products");
    expect(tr("account.myAccount.role", { role: "ROLE_SELLER" })).toBe("Satıcı");
    expect(en("account.myAccount.role", { role: "ROLE_SELLER" })).toBe("Seller");
  });
});
