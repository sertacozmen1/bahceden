import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ScrollingNav } from "./ScrollingNav";

/**
 * Yatay sekme şeridi: etkin seçenek görünür alanın dışındaysa şerit onu
 * ortalar. jsdom düzen hesaplamaz; kutular `getBoundingClientRect` ile verilir.
 * Şerit 0–300 px arasındadır.
 */

type Box = { readonly left: number; readonly width: number };

function boxOf({ left, width }: Box): DOMRect {
  return DOMRect.fromRect({ x: left, y: 0, width, height: 44 });
}

function renderStrip(current: Box, currentKey = "CLOSED") {
  const strip = { left: 0, width: 300 };
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function measure(this: HTMLElement) {
    return boxOf(this.getAttribute("aria-current") === "page" ? current : strip);
  });
  return render(
    <ScrollingNav label="Talep durumu" className="overflow-x-auto" currentKey={currentKey}>
      <ul>
        <li>
          <a href="?status=OPEN">Açık</a>
        </li>
        <li>
          <a href="?status=CLOSED" aria-current="page">
            Kapandı
          </a>
        </li>
      </ul>
    </ScrollingNav>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("ScrollingNav", () => {
  it("etiketli bir gezinti alanı olarak görünür", () => {
    renderStrip({ left: 10, width: 80 });

    expect(screen.getByRole("navigation", { name: "Talep durumu" })).toBeDefined();
  });

  it("etkin seçenek sağda dışarıdaysa şeridi onu ortalayacak kadar kaydırır", () => {
    renderStrip({ left: 420, width: 100 });

    // 420 - 0 - (300 - 100) / 2 = 320
    expect(screen.getByRole("navigation").scrollLeft).toBe(320);
  });

  it("etkin seçenek zaten görünüyorsa şeridi kaydırmaz", () => {
    renderStrip({ left: 120, width: 100 });

    expect(screen.getByRole("navigation").scrollLeft).toBe(0);
  });
});
