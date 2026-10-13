import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { DefectClaimView } from "@/features/claims/api";
import { ClaimCard } from "./ClaimCard";

/** Talep kartı (ADR-086): durum metni, satırlar, korumalı fotoğraf adresleri, tarafa göre süre cümlesi. */

const tomatoLine: DefectClaimView["lines"][number] = {
  orderItemId: 41,
  productName: "Domates",
  unit: "KILOGRAM",
  unitPrice: { amount: 2450, currency: "TRY" },
  claimedQuantity: 2,
};

const claim: DefectClaimView = {
  id: 5,
  status: "OPEN",
  description: "Kasanın üst sırası ezik geldi.",
  lines: [tomatoLine],
  photos: [
    { position: 0, width: 1600, height: 1200 },
    { position: 1, width: 1200, height: 1600 },
  ],
  sellerDeadlineAt: "2026-10-15T10:00:00Z",
  shippingRefunded: false,
  createdAt: "2026-10-13T10:00:00Z",
};

describe("ClaimCard", () => {
  it("alıcıya durumu, satırı, açıklamayı ve korumalı fotoğrafları gösterir", () => {
    renderWithIntl(<ClaimCard claim={claim} viewer="buyer" />);

    expect(screen.getByText("Üreticinin yanıtı bekleniyor")).toBeDefined();
    expect(screen.getByText("Domates")).toBeDefined();
    expect(screen.getByText("Kasanın üst sırası ezik geldi.")).toBeDefined();
    expect(screen.getByText(/Üretici .* tarihine kadar yanıt verecek\./)).toBeDefined();
    const image = screen.getByRole("img", { name: "Sorun fotoğrafı 1" });
    expect(image.getAttribute("src")).toBe("/media/claim-photos/5/0?size=CARD");
    expect(image.closest("a")?.getAttribute("href")).toBe("/media/claim-photos/5/0?size=LARGE");
    expect(screen.getAllByRole("img")).toHaveLength(2);
  });

  it("satıcıya yanıt süresini ve sessiz kalırsa ne olacağını söyler", () => {
    renderWithIntl(<ClaimCard claim={claim} viewer="seller" />);

    expect(screen.getByText(/yanıt vermezseniz talebi Bahçeden ekibi karara bağlar/)).toBeDefined();
  });

  it("teklifte önerilen adeti ve alıcının süresini, sonuçta iade edilen adet ve tutarı gösterir", () => {
    const offered: DefectClaimView = {
      ...claim,
      status: "OFFERED",
      lines: [{ ...tomatoLine, offeredQuantity: 1 }],
      buyerDeadlineAt: "2026-10-17T10:00:00Z",
    };
    const { unmount } = renderWithIntl(<ClaimCard claim={offered} viewer="buyer" />);
    expect(screen.getByText("1 önerildi")).toBeDefined();
    expect(screen.getByText(/teklifi kabul edebilir ya da Bahçeden ekibine taşıyabilirsiniz/)).toBeDefined();
    unmount();

    renderWithIntl(
      <ClaimCard
        claim={{
          ...offered,
          status: "REFUNDED",
          lines: [{ ...tomatoLine, offeredQuantity: 1, refundedQuantity: 1 }],
          refundAmount: { amount: 2450, currency: "TRY" },
        }}
        viewer="seller"
      />,
    );
    expect(screen.getByText("1 iade edildi")).toBeDefined();
    expect(screen.queryByText("1 önerildi")).toBeNull();
    expect(screen.getByText("₺24,50 iade edildi.")).toBeDefined();
  });

  it("satıcının ret gerekçesini ve eylem alanını gösterir", () => {
    renderWithIntl(
      <ClaimCard
        claim={{ ...claim, status: "REJECTED", sellerReason: "Paket sağlamdı.", buyerDeadlineAt: "2026-10-17T10:00:00Z" }}
        viewer="buyer"
        actions={<button type="button">Eylem</button>}
      />,
    );

    expect(screen.getByText("Üreticinin gerekçesi: Paket sağlamdı.")).toBeDefined();
    expect(screen.getByRole("button", { name: "Eylem" })).toBeDefined();
  });

  it("açık olmayan talepte süre cümlesi yoktur", () => {
    renderWithIntl(<ClaimCard claim={{ ...claim, status: "ESCALATED" }} viewer="buyer" />);

    expect(screen.getByText("Bahçeden inceliyor")).toBeDefined();
    expect(screen.queryByText(/yanıt verecek/)).toBeNull();
  });

  it("admine süreleri tarafsız cümleyle söyler", () => {
    const { unmount } = renderWithIntl(<ClaimCard claim={claim} viewer="admin" />);
    expect(screen.getByText(/Üreticinin yanıt süresi .* tarihinde doluyor/)).toBeDefined();
    unmount();

    renderWithIntl(
      <ClaimCard claim={{ ...claim, status: "OFFERED", buyerDeadlineAt: "2026-10-17T10:00:00Z" }} viewer="admin" />,
    );
    expect(screen.getByText(/Alıcının yanıt süresi .* yanıt gelmezse teklif uygulanır/)).toBeDefined();
  });
});
