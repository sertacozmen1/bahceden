import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithIntl } from "@/test/intl";
import type { SessionState } from "@/features/account/api";
import type { SellerOrderDetail } from "@/features/orders/api";
import SellerOrderPage from "./page";

/**
 * Satıcının satıcı siparişi (orders spec'i): hakediş ve alıcının yalnızca il
 * ve ilçesi görünür (K10). Veri taklit edilir.
 */

const session = vi.hoisted(() => ({
  current: {
    kind: "signed-in",
    accessToken: "test-erisim",
    account: { email: "uretici@ornek.test", roles: ["ROLE_CUSTOMER", "ROLE_SELLER"], emailVerified: true },
  } as SessionState,
}));
const getSellerOrder = vi.hoisted(() => vi.fn());
const notFound = vi.hoisted(() =>
  vi.fn(() => {
    throw new Error("bulunamadı");
  }),
);

vi.mock("@/features/account/api", () => ({ currentSession: async () => session.current }));
vi.mock("@/features/orders/api", () => ({ getSellerOrder }));
vi.mock("next/navigation", () => ({ notFound, redirect: vi.fn(), useRouter: () => ({ refresh: vi.fn() }) }));

const money = (amount: number) => ({ amount, currency: "TRY" });

const order: SellerOrderDetail = {
  id: 17,
  orderNumber: "BHC-7K2M9QXA4T",
  status: "NEW",
  createdAt: "2026-10-08T09:30:00Z",
  items: [
    { productSlug: "domates", productName: "Domates", unit: "KILOGRAM", unitPrice: money(24550), quantity: 1, lineTotal: money(24550) },
  ],
  itemsTotal: money(24550),
  commissionAmount: money(1528),
  sellerPayout: money(23022),
  dispatchDays: 2,
  dispatchDueAt: "2026-10-13T20:59:59Z",
  dispatchOverdue: true,
  provinceName: "İzmir",
  district: "Bornova",
  cancellable: true,
};

const params = (id: string) => ({ params: Promise.resolve({ id }) });

beforeEach(() => {
  getSellerOrder.mockReset();
});

describe("Satıcı siparişi sayfası", () => {
  it("satırları, komisyonu, hakedişi, süre aşımını ve yalnızca il/ilçeyi gösterir", async () => {
    getSellerOrder.mockResolvedValue(order);

    renderWithIntl(await SellerOrderPage(params("17")));

    expect(screen.getByRole("heading", { level: 1, name: "BHC-7K2M9QXA4T" })).toBeDefined();
    expect(screen.getByText("−₺15,28")).toBeDefined();
    expect(screen.getByText("₺230,22")).toBeDefined();
    expect(screen.getByText(/Kargoya verme süresi geçti/)).toBeDefined();
    expect(screen.getByText("Teslimat: Bornova / İzmir")).toBeDefined();
    expect(screen.getByRole("button", { name: "Siparişi iptal et" })).toBeDefined();
  });

  it("başkasının veya olmayan sipariş bulunamadı olur", async () => {
    getSellerOrder.mockResolvedValue(null);

    await expect(SellerOrderPage(params("17"))).rejects.toThrow("bulunamadı");
    await expect(SellerOrderPage(params("abc"))).rejects.toThrow("bulunamadı");
  });
});
