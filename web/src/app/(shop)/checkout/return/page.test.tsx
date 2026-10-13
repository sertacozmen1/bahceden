import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { SessionState } from "@/features/account/api";
import type { OrderDetail } from "@/features/orders/api";
import CheckoutReturnPage from "./page";

/**
 * Ödeme dönüşü (cart-and-checkout spec'i, D8): dört sonuç açıkça söylenir.
 * Sipariş ve oturum taklit edilir.
 */

const session = vi.hoisted(() => ({
  current: {
    kind: "signed-in",
    accessToken: "test-erisim",
    account: { email: "alici@ornek.test", roles: ["ROLE_CUSTOMER"], emailVerified: true },
  } as SessionState,
}));
const completePayment = vi.hoisted(() => vi.fn());
const notFound = vi.hoisted(() =>
  vi.fn(() => {
    throw new Error("bulunamadı");
  }),
);

vi.mock("@/features/account/api", () => ({ currentSession: async () => session.current }));
vi.mock("@/features/orders/api", () => ({ completePayment }));
vi.mock("next/navigation", () => ({ notFound, redirect: vi.fn() }));

const money = (amount: number) => ({ amount, currency: "TRY" });

function anOrder(status: OrderDetail["status"]): OrderDetail {
  return {
    orderNumber: "BHC-7K2M9QXA4T",
    status,
    createdAt: "2026-10-08T09:30:00Z",
    shippingAddress: {
      recipientName: "Örnek Alıcı",
      phone: "+905550000000",
      provinceCode: "35",
      provinceName: "İzmir",
      district: "Bornova",
      addressLine: "Ornek Mahallesi No: 1",
    },
    itemsTotal: money(2450),
    shippingTotal: money(9900),
    grandTotal: money(12350),
    sellerOrders: [
      {
        id: 17,
        seller: { slug: "uretici", displayName: "Üretici Bahçesi", city: "Manisa" },
        status: status === "PAID" ? "NEW" : "VOID",
        items: [
          {
            id: 1,
            productSlug: "domates",
            productName: "Domates",
            unit: "KILOGRAM",
            unitPrice: money(2450),
            quantity: 1,
            lineTotal: money(2450),
          },
        ],
        itemsTotal: money(2450),
        shippingFee: money(9900),
        dispatchDays: 2,
        dispatchDueAt: "2026-10-13T20:59:59Z",
        dispatchOverdue: false,
        cancellable: status === "PAID",
      },
    ],
  };
}

const search = (order?: string) => ({ searchParams: Promise.resolve(order === undefined ? {} : { order }) });

beforeEach(() => {
  completePayment.mockReset();
});

describe("Ödeme dönüşü", () => {
  it("ödendi: başarıyı, siparişi ve sipariş bağlantısını gösterir", async () => {
    completePayment.mockResolvedValue(anOrder("PAID"));

    renderWithIntl(await CheckoutReturnPage(search("BHC-7K2M9QXA4T")));

    expect(screen.getByRole("heading", { level: 1, name: "Siparişiniz alındı" })).toBeDefined();
    expect(screen.getByText("Üretici Bahçesi")).toBeDefined();
    expect(screen.getByRole("link", { name: "Siparişi gör" }).getAttribute("href")).toBe("/account/orders/BHC-7K2M9QXA4T");
    expect(completePayment).toHaveBeenCalledWith("test-erisim", "BHC-7K2M9QXA4T");
  });

  it.each([
    ["PAYMENT_FAILED", "Ödeme başarısız oldu"],
    ["PAYMENT_EXPIRED", "Ödeme süresi doldu"],
    ["CANCELLED_OUT_OF_STOCK", "Ürünler tükendi, ödemeniz iade ediliyor"],
  ] as const)("%s: sonucu söyler ve sepete döndürür", async (status, heading) => {
    completePayment.mockResolvedValue(anOrder(status));

    renderWithIntl(await CheckoutReturnPage(search("BHC-7K2M9QXA4T")));

    expect(screen.getByRole("heading", { level: 1, name: heading })).toBeDefined();
    expect(screen.getByRole("link", { name: "Sepete dön" }).getAttribute("href")).toBe("/cart");
    expect(screen.queryByText("Üretici Bahçesi")).toBeNull();
  });

  it("ödeme bekliyorsa yeniden kontrol bağlantısı verir", async () => {
    completePayment.mockResolvedValue(anOrder("PENDING_PAYMENT"));

    renderWithIntl(await CheckoutReturnPage(search("BHC-7K2M9QXA4T")));

    expect(screen.getByRole("link", { name: "Yeniden kontrol et" }).getAttribute("href")).toBe(
      "/checkout/return?order=BHC-7K2M9QXA4T",
    );
  });

  it("sipariş numarası yoksa veya sipariş bulunamazsa bulunamadı olur", async () => {
    await expect(CheckoutReturnPage(search())).rejects.toThrow("bulunamadı");
    completePayment.mockResolvedValue(null);
    await expect(CheckoutReturnPage(search("BHC-YOK"))).rejects.toThrow("bulunamadı");
  });
});
