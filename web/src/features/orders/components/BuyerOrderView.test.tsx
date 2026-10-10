import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { OrderDetail } from "@/features/orders/api";
import { BuyerOrderView } from "./BuyerOrderView";

/**
 * Alıcının sipariş detayında gönderi (orders spec'i "Alıcı kendi siparişlerini
 * görür", add-shipments): firma, takip numarası, durum; etiket ve kopyalama
 * yalnızca satıcıdadır.
 */

const money = (amount: number) => ({ amount, currency: "TRY" });

function orderWith(sellerOrder: Partial<OrderDetail["sellerOrders"][number]>): OrderDetail {
  return {
    orderNumber: "BHC-7K2M9QXA4T",
    status: "PAID",
    createdAt: "2026-10-08T09:30:00Z",
    paidAt: "2026-10-08T09:31:00Z",
    shippingAddress: {
      recipientName: "Örnek Alıcı",
      phone: "+905550000000",
      provinceCode: "35",
      provinceName: "İzmir",
      district: "Bornova",
      addressLine: "Ornek Mahallesi No: 1",
    },
    itemsTotal: money(4900),
    shippingTotal: money(9900),
    grandTotal: money(14800),
    sellerOrders: [
      {
        id: 17,
        seller: { slug: "uretici", displayName: "Üretici Bahçesi", city: "Manisa" },
        status: "SHIPPED",
        items: [],
        itemsTotal: money(4900),
        shippingFee: money(9900),
        dispatchDays: 2,
        dispatchOverdue: false,
        cancellable: false,
        ...sellerOrder,
      },
    ],
  };
}

describe("BuyerOrderView", () => {
  it("kargodaki satıcı siparişinde firma, takip numarası ve durum görünür; etiket yoktur", () => {
    renderWithIntl(
      <BuyerOrderView
        order={orderWith({
          shipment: {
            carrier: "Ornek Kargo",
            trackingNumber: "FK0123456789",
            status: "IN_TRANSIT",
            packageSize: "MEDIUM",
            createdAt: "2026-10-10T08:15:00Z",
            handedOverAt: "2026-10-10T14:30:00Z",
          },
        })}
      />,
    );

    expect(screen.getByText("Ornek Kargo")).toBeDefined();
    expect(screen.getByText("FK0123456789")).toBeDefined();
    expect(screen.getByText("Yolda")).toBeDefined();
    expect(screen.getByText("Kargoda")).toBeDefined();
    expect(screen.queryByRole("link", { name: "Etiketi aç ve yazdır" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Kodu kopyala" })).toBeNull();
  });

  it("teslim edilemeyen satıcı siparişinde alıcıya açıklama gösterir", () => {
    renderWithIntl(<BuyerOrderView order={orderWith({ status: "DELIVERY_FAILED" })} />);

    expect(screen.getByText(/Paket size teslim edilemedi/)).toBeDefined();
  });
});
