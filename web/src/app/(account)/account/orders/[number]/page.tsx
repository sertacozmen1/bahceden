import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { getTranslations } from "next-intl/server";
import { buttonVariants } from "@/components/ui/button";
import { requireSignedIn } from "@/features/account/requireSession";
import { getOwnOrder } from "@/features/orders/api";
import { BuyerOrderView } from "@/features/orders/components/BuyerOrderView";
import { CancelSellerOrder } from "@/features/orders/components/CancelSellerOrder";

type Params = { readonly params: Promise<{ number: string }> };

/** Başlık sipariş numarasıdır. */
export async function generateMetadata({ params }: Params): Promise<Metadata> {
  const t = await getTranslations();
  return { title: t("orders.detailTitle", { number: decodeURIComponent((await params).number) }) };
}

/**
 * Alıcının sipariş detayı (orders spec'i). Başkasının veya olmayan sipariş
 * "bulunamadı" olur (sahiplik `api`'de sorgu seviyesinde). Kargoya verilmemiş
 * satıcı siparişi buradan iptal edilir (ADR-056 §1).
 */
export default async function MyOrderPage({ params }: Params) {
  const orderNumber = decodeURIComponent((await params).number);
  const t = await getTranslations();
  const { accessToken } = await requireSignedIn("/account/orders");
  const order = await getOwnOrder(accessToken, orderNumber);
  if (order === null) {
    notFound();
  }
  return (
    <main className="mx-auto w-full max-w-3xl px-4 pt-8 pb-12 lg:pt-12">
      <Link className={buttonVariants({ variant: "ghost", className: "-ml-3 mb-4" })} href="/account/orders">
        <ArrowLeft aria-hidden />
        {t("orders.backToList")}
      </Link>
      <h1 className="mb-4 text-2xl font-semibold tracking-tight tabular-nums lg:text-3xl">
        {t("orders.detailTitle", { number: order.orderNumber })}
      </h1>
      <BuyerOrderView
        order={order}
        sellerOrderAction={(sellerOrder) =>
          sellerOrder.cancellable ? (
            <CancelSellerOrder
              as="buyer"
              orderNumber={order.orderNumber}
              sellerOrderId={sellerOrder.id}
              sellerName={sellerOrder.seller.displayName}
            />
          ) : null
        }
      />
    </main>
  );
}
