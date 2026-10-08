import { describe, expect, it } from "vitest";
import { formatMoney, parseMinorUnits, toPriceInput } from "./money";

/** Para gösterimi ve fiyat girdisi (ADR-006, add-product-listings). */
describe("parseMinorUnits", () => {
  it.each([
    ["24", 2400],
    ["24,5", 2450],
    ["24,50", 2450],
    ["24.50", 2450],
    ["₺ 24,50", 2450],
    ["24,50 TL", 2450],
    ["1.250", 125000],
    ["1.250,50", 125050],
    ["1,250.50", 125050],
    ["0,01", 1],
    ["24,505", 2450500],
  ])("%s → %i kuruş", (input, expected) => {
    expect(parseMinorUnits(input)).toBe(expected);
  });

  it.each(["", "abc", "24,5055", "12.34.567", "-5", "24,", "1e5"])("geçersiz girdi: %s", (input) => {
    expect(parseMinorUnits(input)).toBeUndefined();
  });
});

describe("toPriceInput", () => {
  it("kuruşu seçili dilde, binlik ayracı olmadan yazar", () => {
    expect(toPriceInput(125050, "tr")).toBe("1250,50");
    expect(toPriceInput(2450, "en")).toBe("24.50");
  });

  it("formatMoney ile tutarlıdır", () => {
    expect(formatMoney({ amount: 2450, currency: "TRY" }, "tr")).toContain("24,50");
  });
});
