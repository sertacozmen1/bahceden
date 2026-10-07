import { describe, expect, it } from "vitest";
import { negotiateLocale, parseLocale, resolveLocale } from "./locale";

/**
 * Dil seçimi (localization spec'i "Dil seçimi", ADR-030): çerez → tarayıcı
 * başlığı → Türkçe.
 */
describe("parseLocale", () => {
  it("desteklenen dili ve bölgeli biçimini tanır", () => {
    expect(parseLocale("en")).toBe("en");
    expect(parseLocale("EN-gb")).toBe("en");
    expect(parseLocale("tr_TR")).toBe("tr");
  });

  it("desteklenmeyen veya boş değeri tanımaz", () => {
    expect(parseLocale("de")).toBeUndefined();
    expect(parseLocale("")).toBeUndefined();
    expect(parseLocale(undefined)).toBeUndefined();
  });
});

describe("negotiateLocale", () => {
  it("ağırlığı en yüksek desteklenen dili seçer", () => {
    expect(negotiateLocale("en-GB,en;q=0.9,tr;q=0.5")).toBe("en");
    expect(negotiateLocale("de-DE,de;q=0.9,tr;q=0.8,en;q=0.7")).toBe("tr");
  });

  it("eşit ağırlıkta başlıktaki sırayı korur", () => {
    expect(negotiateLocale("en, tr")).toBe("en");
  });

  it("ağırlığı 0 olan dili kabul etmez", () => {
    expect(negotiateLocale("en;q=0, tr;q=0.1")).toBe("tr");
  });

  it("desteklenen dil yoksa veya başlık yoksa sonuç vermez", () => {
    expect(negotiateLocale("de-DE,fr;q=0.8")).toBeUndefined();
    expect(negotiateLocale(null)).toBeUndefined();
  });
});

describe("resolveLocale", () => {
  it("kaydedilmiş seçim tarayıcı dilini ezer", () => {
    expect(resolveLocale("tr", "en-US,en;q=0.9")).toBe("tr");
  });

  it("çerez yoksa tarayıcı dili, o da yoksa Türkçe", () => {
    expect(resolveLocale(undefined, "en-US")).toBe("en");
    expect(resolveLocale(undefined, "de-DE")).toBe("tr");
    expect(resolveLocale("bozuk", null)).toBe("tr");
  });
});
